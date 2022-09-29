/*******************************************************************************
 * Copyright (c) 2022, 2022 SAP AG & IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - initial implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("thread_stack")
@Icon("/META-INF/icons/stack_frame.gif")
@Subject("java.lang.Thread")
public class ThreadStackQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument threadIds;

    public IResult execute(IProgressListener listener) throws Exception
    {
        InspectionAssert.heapFormatIsNot(snapshot, "phd"); //$NON-NLS-1$

        StringBuilder builder = new StringBuilder();

        for (int[] ids : threadIds)
        {
            for (int threadId : ids)
            {
                IThreadStack stack = snapshot.getThreadStack(threadId);
                if (stack == null)
                {
                    return new TextResult(MessageUtil.format(Messages.ThreadStackQuery_No_Stack, "0x" //$NON-NLS-1$
                                    + Long.toHexString(snapshot.getObject(threadId).getObjectAddress())), false);
                }

                IObject threadObject = snapshot.getObject(threadId);
                builder.append(threadObject.getTechnicalName()).append(" : ") //$NON-NLS-1$
                                .append(threadObject.getClassSpecificName()).append("\r\n"); //$NON-NLS-1$
                for (IStackFrame frame : stack.getStackFrames())
                {
                    builder.append("  ").append(frame.getText()).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
                }

                builder.append("\r\n"); //$NON-NLS-1$
            }
        }

        return new TextResult(builder.toString(), false);
    }
}
