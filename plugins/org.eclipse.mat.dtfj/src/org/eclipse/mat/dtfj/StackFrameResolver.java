/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;

@Subjects({DTFJIndexBuilder.METHOD, DTFJIndexBuilder.STACK_FRAME})
public class StackFrameResolver implements IClassSpecificNameResolver
{

    public StackFrameResolver()
    {}

    public String resolve(IObject object) throws SnapshotException
    {
        String methodName = (String) object.resolveValue(DTFJIndexBuilder.METHOD_NAME);
        String fileName = (String) object.resolveValue(DTFJIndexBuilder.FILE_NAME);
        Integer lineNumber = (Integer) object.resolveValue(DTFJIndexBuilder.LINE_NUMBER);
        String ret;
        if (fileName != null && lineNumber != null)
        {
            if (methodName == null)
                ret = MessageFormat.format(Messages.StackFrameResolver_file_line, fileName, lineNumber);
            else
                ret =MessageFormat.format(Messages.StackFrameResolver_method_file_line, methodName, fileName, lineNumber);
        }
        else if (fileName != null) {
            if (methodName == null)
                ret = MessageFormat.format(Messages.StackFrameResolver_file, fileName);
            else
                ret = MessageFormat.format(Messages.StackFrameResolver_method_file, methodName, fileName);
        } else if (methodName != null) {
            ret = MessageFormat.format(Messages.StackFrameResolver_method, methodName);
        } else {
            ret = null;
        }
        return ret;
    }

}
