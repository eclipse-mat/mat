/*******************************************************************************
 * Copyright (c) 2010, 2020 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IThreadDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

import com.ibm.dtfj.image.CorruptData;
import com.ibm.dtfj.image.CorruptDataException;
import com.ibm.dtfj.image.DataUnavailable;
import com.ibm.dtfj.image.ImagePointer;
import com.ibm.dtfj.image.ImageStackFrame;
import com.ibm.dtfj.image.ImageThread;
import com.ibm.dtfj.java.JavaMonitor;
import com.ibm.dtfj.java.JavaObject;
import com.ibm.dtfj.java.JavaRuntime;
import com.ibm.dtfj.java.JavaThread;
import com.ibm.icu.text.DecimalFormat;

/**
 * Use DTFJ to extract extra information about a thread.
 * @author ajohnson
 *
 */
public class ThreadDetailsResolver implements IThreadDetailsResolver
{
    private final ThreadDetailsResolver1 delegate;

    /**
     * Create a DTFJ thread details resolver. If DTFJ is not available, create a
     * resolver that does nothing.
     */
    public ThreadDetailsResolver()
    {
        ThreadDetailsResolver1 delegate = null;
        try
        {
            delegate = new ThreadDetailsResolver1();
        }
        catch (NoClassDefFoundError e)
        {
            // If the DTFJ feature is not available then this might happen, so ignore.
        }
        this.delegate = delegate;
    }

    /**
     * The columns that can be extracted via DTFJ
     */
    public Column[] getColumns()
    {
        if (delegate != null)
            return delegate.getColumns();
        return null;
    }

    /**
     * Add basic DTFJ information.
     */
    public void complementShallow(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        if (delegate != null)
            delegate.complementShallow(thread, listener);
    }

    /**
     * Add detailed DTFJ information, including native thread stack.
     */
    public void complementDeep(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        if (delegate != null)
            delegate.complementDeep(thread, listener);
    }
}

class ThreadDetailsResolver1 implements IThreadDetailsResolver
{
    /**
     * Simple constructor which tests that DTFJ is available
     */
    ThreadDetailsResolver1()
    {
        // Check that JavaRuntime and so DTFJ is available
        JavaRuntime.class.getName();
    }

    DecimalFormat hex = new HexFormat();
    /**
     * Formatter to display addresses etc. in hex
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
     * The columns that can be extracted via DTFJ
     */
    public Column[] getColumns()
    {
        return new Column[]{
                        new Column(Messages.ThreadDetailsResolver_DTFJ_Name),
                        (new Column(Messages.ThreadDetailsResolver_JNIEnv, Long.class).noTotals().formatting(hex)),
                        (new Column(Messages.ThreadDetailsResolver_Priority, Integer.class).noTotals()),
                        new Column(Messages.ThreadDetailsResolver_State),
                        (new Column(Messages.ThreadDetailsResolver_State_value, Integer.class).noTotals().formatting(hex)),
                        new Column(Messages.ThreadDetailsResolver_Native_id)};
    }

    public void complementShallow(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        // Find the thread
        JavaThread jt = getJavaThread(thread, listener);
        if (jt != null)
        {
            // Set the column data, ignore errors
            Column cols[] = getColumns();
            try
            {
                thread.setValue(cols[0], jt.getName());
            }
            catch (CorruptDataException e)
            {}
            try
            {
                ImagePointer ip = jt.getJNIEnv();
                if (ip != null)
                {
                    thread.setValue(cols[1], ip.getAddress());
                }
            }
            catch (CorruptDataException e)
            {}
            try
            {
                thread.setValue(cols[2], jt.getPriority());
            }
            catch (CorruptDataException e)
            {}
            try
            {
                int state = jt.getState();
                String stateName = printableState(state);
                thread.setValue(cols[3], stateName);
                thread.setValue(cols[4], state);
            }
            catch (CorruptDataException e)
            {}
            try
            {
                ImageThread it = jt.getImageThread();
                String id = it.getID();
                thread.setValue(cols[5], id);
            }
            catch (DataUnavailable e)
            {}
            catch (CorruptDataException e)
            {}
        }
    }

    /**
     * Convert the thread state to a readable value
     * @param state - a collection of state bits
     * @return a readable list of states 
     */
    private String printableState(int state)
    {
        ArrayList<String> al = new ArrayList<String>();
        if ((state & JavaThread.STATE_ALIVE) != 0)
            al.add(Messages.ThreadDetailsResolver_alive);
        if ((state & JavaThread.STATE_BLOCKED_ON_MONITOR_ENTER) != 0)
            al.add(Messages.ThreadDetailsResolver_blocked_on_monitor_enter);
        if ((state & JavaThread.STATE_IN_NATIVE) != 0)
            al.add(Messages.ThreadDetailsResolver_in_native);
        if ((state & JavaThread.STATE_IN_OBJECT_WAIT) != 0)
            al.add(Messages.ThreadDetailsResolver_in_object_wait);
        if ((state & JavaThread.STATE_INTERRUPTED) != 0)
            al.add(Messages.ThreadDetailsResolver_interrupted);
        if ((state & JavaThread.STATE_PARKED) != 0)
            al.add(Messages.ThreadDetailsResolver_parked);
        if ((state & JavaThread.STATE_RUNNABLE) != 0)
            al.add(Messages.ThreadDetailsResolver_runnable);
        if ((state & JavaThread.STATE_SLEEPING) != 0)
            al.add(Messages.ThreadDetailsResolver_sleeping);
        if ((state & JavaThread.STATE_SUSPENDED) != 0)
            al.add(Messages.ThreadDetailsResolver_suspended);
        if ((state & JavaThread.STATE_TERMINATED) != 0)
            al.add(Messages.ThreadDetailsResolver_terminated);
        if ((state & JavaThread.STATE_VENDOR_1) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor1);
        if ((state & JavaThread.STATE_VENDOR_2) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor2);
        if ((state & JavaThread.STATE_VENDOR_3) != 0)
            al.add(Messages.ThreadDetailsResolver_vendor3);
        if ((state & JavaThread.STATE_WAITING) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting);
        if ((state & JavaThread.STATE_WAITING_INDEFINITELY) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting_indefinitely);
        if ((state & JavaThread.STATE_WAITING_WITH_TIMEOUT) != 0)
            al.add(Messages.ThreadDetailsResolver_waiting_with_timeout);
        return al.toString();
    }

    /**
     * Find a JavaThread associated with a thread object.
     * @param thread
     * @param listener
     * @return the JavaThread, or null if not found or not a DTFJ dump
     * @throws SnapshotException
     */
    private JavaThread getJavaThread(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        IObject object = thread.getThreadObject();
        JavaRuntime jr = object.getSnapshot().getSnapshotAddons(JavaRuntime.class);
        if (jr != null)
        {
            for (Iterator<?> it = jr.getThreads(); it.hasNext(); )
            {
                Object o = it.next();
                if (o instanceof JavaThread)
                {
                    JavaThread jt = (JavaThread)o;
                    long addr = DTFJIndexBuilder.getThreadAddress(jt, listener);
                    if (addr == object.getObjectAddress())
                    {
                        return jt;
                    }
                }
            }
        }
        return null;
    }

    public void complementDeep(IThreadInfo thread, IProgressListener listener) throws SnapshotException
    {
        complementShallow(thread, listener);
        JavaThread jt = getJavaThread(thread, listener);
        if (jt != null)
        {
            // Extract the native stack
            try
            {
                ImageThread it = jt.getImageThread();
                StringBuilder sb = new StringBuilder();
                for (Iterator<?> sfs = it.getStackFrames(); sfs.hasNext();)
                {
                    Object o = sfs.next();
                    if (o instanceof ImageStackFrame)
                    {
                        ImageStackFrame sf = (ImageStackFrame) o;
                        try
                        {
                            sb.append(sf.getProcedureName());
                        }
                        catch (CorruptDataException e)
                        {
                            sb.append(e.toString());
                        }
                        sb.append('\n');
                    }
                }
                TextResult tr = new TextResult(sb.toString());
                thread.addDetails(Messages.ThreadDetailsResolver_Native_stack, tr);
            }
            catch (DataUnavailable e)
            {}
            catch (CorruptDataException e)
            {}
            try
            {
                ISnapshot snapshot = thread.getThreadObject().getSnapshot();
                JavaObject bo = null;
                try
                {
                    bo = jt.getBlockingObject();
                }
                catch (DataUnavailable e)
                {}
                catch (CorruptDataException e)
                {}
                long addr = 0;
                ArrayInt blocking = new ArrayInt();
                if (bo != null)
                {
                    addr = bo.getID().getAddress();
                    int id = snapshot.mapAddressToId(addr);
                    blocking.add(id);
                }
                ArrayInt owned = new ArrayInt();
                ArrayInt owners = new ArrayInt();
                ArrayInt enters = new ArrayInt();
                ArrayInt waits = new ArrayInt();
                JavaRuntime jr = snapshot.getSnapshotAddons(JavaRuntime.class);
                if (jr != null)
                {
                    for (Iterator<?> it = jr.getMonitors(); it.hasNext();)
                    {
                        Object o = it.next();
                        if (o instanceof CorruptData)
                            continue;
                        if (o instanceof JavaMonitor)
                        {
                            JavaMonitor jm = (JavaMonitor)o;
                            JavaObject jo = jm.getObject();
                            if (jo != null)
                            {
                                // Found monitor with object
                                JavaThread jth = jm.getOwner();
                                if (jth != null)
                                {
                                    long addr2 = jth.getObject().getID().getAddress();
                                    if (addr2 == thread.getThreadObject().getObjectAddress())
                                    {
                                        long addr3 = jo.getID().getAddress();
                                        try
                                        {
                                            int id2 = snapshot.mapAddressToId(addr3);
                                            owned.add(id2);
                                        }
                                        catch (SnapshotException e)
                                        {}
                                    }
                                }
                                if (jo.equals(bo))
                                {
                                    // Found blocking
                                    JavaThread jth2 = jm.getOwner();
                                    if (jth2 != null)
                                    {
                                        long addr2 = jth2.getObject().getID().getAddress();
                                        try
                                        {
                                            int id2 = snapshot.mapAddressToId(addr2);
                                            owners.add(id2);
                                        }
                                        catch (SnapshotException e)
                                        {}
                                    }
                                    for (Iterator<?> it2 = jm.getEnterWaiters(); it2.hasNext();)
                                    {
                                        Object o2 = it2.next();
                                        if (o2 instanceof CorruptData)
                                            continue;
                                        if (o2 instanceof JavaThread)
                                        {
                                            jth = (JavaThread)o2;
                                            long addr2 = jth.getObject().getID().getAddress();
                                            try
                                            {
                                                int id2 = snapshot.mapAddressToId(addr2);
                                                enters.add(id2);
                                            }
                                            catch (SnapshotException e)
                                            {}
                                        }
                                    }
                                    for (Iterator<?> it2 = jm.getNotifyWaiters(); it2.hasNext();)
                                    {
                                        Object o2 = it2.next();
                                        if (o2 instanceof CorruptData)
                                            continue;
                                        if (o2 instanceof JavaThread)
                                        {
                                            jth = (JavaThread)o2;
                                            long addr2 = jth.getObject().getID().getAddress();
                                            try
                                            {
                                                int id2 = snapshot.mapAddressToId(addr2);
                                                waits.add(id2);
                                            }
                                            catch (SnapshotException e)
                                            {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (true)
                {
                    IResult res2 = new ObjectListResult.Outbound(snapshot, owned.toArray());
                    thread.addDetails(Messages.ThreadDetailsResolver_OwnerMonitors, res2);
                }
                if (!blocking.isEmpty())
                {
                    if (true)
                    {
                        IResult res2 = new ObjectListResult.Outbound(snapshot, blocking.toArray());
                        thread.addDetails(Messages.ThreadDetailsResolver_ThreadBlockedOn, res2);
                    }
                    if (true)
                    {
                        IResult res2 = new ObjectListResult.Outbound(snapshot, owners.toArray());
                        thread.addDetails(Messages.ThreadDetailsResolver_CurrentOwner, res2);
                    }
                    if (true)
                    {
                        IResult res2 = new ObjectListResult.Outbound(snapshot, enters.toArray());
                        thread.addDetails(Messages.ThreadDetailsResolver_WaitEnterThreads, res2);
                    }
                    if (true)
                    {
                        IResult res2 = new ObjectListResult.Outbound(snapshot, waits.toArray());
                        thread.addDetails(Messages.ThreadDetailsResolver_WaitNotifyThreads, res2);
                    }
                }
            }
            catch (CorruptDataException e1)
            {}
            catch (SnapshotException e1)
            {}
        }
    }

}
