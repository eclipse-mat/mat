/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

@Subjects({"<method>", "<stack frame>"})
public class StackFrameResolver implements IClassSpecificNameResolver
{

    public StackFrameResolver()
    {}

    public String resolve(IObject object) throws SnapshotException
    {
        String methodName = (String) object.resolveValue("methodName");
        String fileName = (String) object.resolveValue("fileName");
        if (fileName == null)
            fileName = (String) object.getClazz().resolveValue("fileName");
        Integer lineNumber = (Integer) object.resolveValue("lineNumber");
        Integer compLevel = (Integer) object.resolveValue("compilationLevel");
        Boolean nativ = (Boolean) object.resolveValue("native");
        String ret;
        if (fileName != null && lineNumber != null && lineNumber > 0)
        {
            if (methodName == null)
                if (Boolean.TRUE.equals(nativ))
                    ret = MessageUtil.format(Messages.StackFrameResolver_file_line_native, fileName, lineNumber);
                else if (compLevel != null && compLevel > 0)
                    ret = MessageUtil.format(Messages.StackFrameResolver_file_line_compiled, fileName, lineNumber);
                else
                    ret = MessageUtil.format(Messages.StackFrameResolver_file_line, fileName, lineNumber);
            else
                if (Boolean.TRUE.equals(nativ))
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file_line_native, methodName, fileName, lineNumber);
                else if (compLevel != null && compLevel > 0)
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file_line_compiled, methodName, fileName, lineNumber);
                else
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file_line, methodName, fileName, lineNumber);
        }
        else if (fileName != null) {
            if (methodName == null)
                if (Boolean.TRUE.equals(nativ))
                    ret = MessageUtil.format(Messages.StackFrameResolver_file_native, fileName);
                else if (compLevel != null && compLevel > 0)
                    ret = MessageUtil.format(Messages.StackFrameResolver_file_compiled, fileName);
                else
                    ret = MessageUtil.format(Messages.StackFrameResolver_file, fileName);
            else
                if (Boolean.TRUE.equals(nativ))
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file_native, methodName, fileName);
                else if (compLevel != null && compLevel > 0)
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file_compiled, methodName, fileName);
                else
                    ret = MessageUtil.format(Messages.StackFrameResolver_method_file, methodName, fileName);
        } else if (methodName != null) {
            ret = MessageUtil.format(Messages.StackFrameResolver_method, methodName);
        } else {
            ret = ""; //$NON-NLS-1$
        }
        return ret;
    }

}
