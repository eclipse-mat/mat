/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.snapshot.extension.IThreadDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.util.IProgressListener;

import com.ibm.icu.text.DecimalFormat;

/**
 * Extract extra information about an OpenJDK thread.
 *
 */
public class ThreadDetailsResolver implements IThreadDetailsResolver
{
    /**
     * Create a Java thread details resolver.
     */
    public ThreadDetailsResolver()
    {
    }

    DecimalFormat hex = new HexFormat();
    /**
     * Formatter to display addresses etc. in hex
     * Copied from {@link org.eclipse.mat.dtfj.ThreadDetailsResolver1.HexFormat}
     */
    static class HexFormat extends DecimalFormat
    {
        /** Regex for matching a hex number, don't allow positive sign */
        private static final String JAVA_HEX_PATTERN = "[-]?(0x|0X|#)\\p{XDigit}+"; //$NON-NLS-1$
        /** Anything that {@link Long#decode(String)} can parse */
        private static final String JAVA_LONG_PATTERN = "[+-]?((0x|0X|#)(\\p{XDigit}+))|(\\p{Digit}+)"; //$NON-NLS-1$
        private static final long serialVersionUID = -420084952258370133L;

        @Override
        public StringBuffer format(long val, StringBuffer buf, FieldPosition fieldPosition)
        {
            fieldPosition.setBeginIndex(buf.length());
            buf.append("0x").append(Long.toHexString(val)); //$NON-NLS-1$
            fieldPosition.setEndIndex(buf.length());
            return buf;
        }

        @Override
        public StringBuffer format(double val, StringBuffer buf, FieldPosition fieldPosition)
        {
            return format((long)val, buf, fieldPosition);
        }

        @Override
        public Number parse(String text) throws ParseException
        {
            if (!text.matches(JAVA_HEX_PATTERN + ".*")) //$NON-NLS-1$
                return super.parse(text);
            ParsePosition p = new ParsePosition(0);
            Number l = parse(text, p);
            if (l == null || p.getIndex() == 0)
                throw new ParseException(text, 0);
            return l;
        }

        @Override
        public Number parse(String text, ParsePosition p)
        {
            if (!text.matches(JAVA_HEX_PATTERN + ".*")) //$NON-NLS-1$
                return super.parse(text, p);
            // Parsing needs to ignore extra text
            int start = p.getIndex();
            String text1 = text.substring(start).replaceFirst("((" + JAVA_LONG_PATTERN + ").*)", "$2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            try
            {
                long l = Long.decode(text1);
                p.setIndex(start + text1.length());
                return l;
            }
            catch (NumberFormatException e)
            {
                String text2 = text1.replaceFirst(JAVA_LONG_PATTERN, "$3"); //$NON-NLS-1$
                if (text2.length() > 0)
                {
                    try
                    {
                        // Large hex value
                        BigInteger b1 = new BigInteger(text2, 16);
                        if (text1.startsWith("-")) //$NON-NLS-1$
                            b1 = b1.negate();
                        p.setIndex(start + text1.length());
                        return b1.longValue();
                    }
                    catch (NumberFormatException e2)
                    {
                        return null;
                    }
                }
                text2 = text1.replaceFirst(JAVA_LONG_PATTERN, "$4"); //$NON-NLS-1$
                if (text2.length() > 0)
                {
                    try
                    {
                        // Large integer
                        BigInteger b1 = new BigInteger(text2, 10);
                        if (text1.startsWith("-")) //$NON-NLS-1$
                            b1 = b1.negate();
                        p.setIndex(start + text1.length());
                        return b1.longValue();
                    }
                    catch (NumberFormatException e2)
                    {
                        return null;
                    }
                }
                return null;
            }
        }
    };

    /**
     * The columns that can be extracted from java.lang.Thread fields.
     */
    public Column[] getColumns()
    {
        return new Column[]{
                        (new Column(Messages.ThreadDetailsResolver_Priority, Integer.class).noTotals()),
                        new Column(Messages.ThreadDetailsResolver_State),
                        (new Column(Messages.ThreadDetailsResolver_State_value, Integer.class).noTotals().formatting(hex))};
    }

    public void complementShallow(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        // Find the thread
        // Set the column data, ignore errors
        Column cols[] = getColumns();
        Object o = thread.getThreadObject().resolveValue("priority"); //$NON-NLS-1$
        if (o instanceof Integer)
        {
            // Let another resolver for priority override this
            if (thread instanceof ThreadInfoImpl && ((ThreadInfoImpl)thread).getValue(cols[0]) == null)
                thread.setValue(cols[0], o);
        }
        o = thread.getThreadObject().resolveValue("threadStatus"); //$NON-NLS-1$
        if (o instanceof Integer)
        {
            int state = (Integer)o;
            String stateName = printableState(state);
            thread.setValue(cols[1], stateName);
            thread.setValue(cols[2], state);
        }
    }

    private static final int THREAD_STATE_ALIVE = 0x00000001;
    private static final int THREAD_STATE_TERMINATED = 0x00000002;
    private static final int THREAD_STATE_RUNNABLE = 0x00000004;
    private static final int THREAD_STATE_WAITING_INDEFINITELY = 0x00000010;
    private static final int THREAD_STATE_WAITING_WITH_TIMEOUT = 0x00000020;
    private static final int THREAD_STATE_SLEEPING = 0x00000040;
    private static final int THREAD_STATE_WAITING = 0x00000080;
    private static final int THREAD_STATE_IN_OBJECT_WAIT = 0x00000100;
    private static final int THREAD_STATE_PARKED = 0x00000200;
    private static final int THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x00000400;
    private static final int THREAD_STATE_SUSPENDED = 0x00100000;
    private static final int THREAD_STATE_INTERRUPTED = 0x00200000;
    private static final int THREAD_STATE_IN_NATIVE = 0x00400000;
    private static final int THREAD_STATE_VENDOR_1 = 0x10000000;
    private static final int THREAD_STATE_VENDOR_2 = 0x20000000;
    private static final int THREAD_STATE_VENDOR_3 = 0x40000000;

    /**
     * Convert the thread state to a readable value
     * @param state - a collection of state bits
     * @return a readable list of states 
     */
    private String printableState(int state)
    {
        ArrayList<String> al = new ArrayList<String>();
        if ((state & THREAD_STATE_ALIVE) != 0)
            al.add(Messages.ThreadDetailsResolver_alive);
        if ((state & THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0)
            al.add(Messages.ThreadDetailsResolver_blocked_on_monitor_enter);
        if ((state & THREAD_STATE_IN_NATIVE) != 0)
            al.add(Messages.ThreadDetailsResolver_in_native);
        if ((state & THREAD_STATE_IN_OBJECT_WAIT) != 0)
            al.add(Messages.ThreadDetailsResolver_in_object_wait);
        if ((state & THREAD_STATE_INTERRUPTED) != 0)
            al.add(Messages.ThreadDetailsResolver_interrupted);
        if ((state & THREAD_STATE_PARKED) != 0)
            al.add(Messages.ThreadDetailsResolver_parked);
        if ((state & THREAD_STATE_RUNNABLE) != 0)
            al.add(Messages.ThreadDetailsResolver_runnable);
        if ((state & THREAD_STATE_SLEEPING) != 0)
            al.add(Messages.ThreadDetailsResolver_sleeping);
        if ((state & THREAD_STATE_SUSPENDED) != 0)
            al.add(Messages.ThreadDetailsResolver_suspended);
        if ((state & THREAD_STATE_TERMINATED) != 0)
            al.add(Messages.ThreadDetailsResolver_terminated);
        if ((state & THREAD_STATE_VENDOR_1) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor1);
        if ((state & THREAD_STATE_VENDOR_2) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor2);
        if ((state & THREAD_STATE_VENDOR_3) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor3);
        if ((state & THREAD_STATE_WAITING) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting);
        if ((state & THREAD_STATE_WAITING_INDEFINITELY) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting_indefinitely);
        if ((state & THREAD_STATE_WAITING_WITH_TIMEOUT) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting_with_timeout);
        return al.toString();
    }

    public void complementDeep(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        complementShallow(thread, listener);
    }
}
