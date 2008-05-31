/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot;

import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.PrettyPrinter;

public class CommonNameResolver
{
    @Subject("java.lang.String")
    public static class StringResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            return PrettyPrinter.objectAsString(obj, 1024);
        }
    }

    @Subject("java.lang.Thread")
    public static class ThreadResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            IObject name = (IObject) obj.resolveValue("name");
            return name != null ? name.getClassSpecificName() : null;
        }
    }

    @Subject("java.lang.ThreadGroup")
    public static class ThreadGroupResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject nameString = (IObject) object.resolveValue("name");
            if (nameString == null)
                return null;
            return nameString.getClassSpecificName();
        }
    }

    @Subjects( { "java.lang.Byte", //
                    "java.lang.Character", //
                    "java.lang.Short", //
                    "java.lang.Integer", //
                    "java.lang.Long", //
                    "java.lang.Float", //
                    "java.lang.Double", //
                    "java.lang.Boolean" })
    public static class ValueResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            return String.valueOf(heapObject.resolveValue("value"));
        }
    }

    @Subject("char[]")
    public static class CharArrayResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IPrimitiveArray charArray = (IPrimitiveArray) heapObject;
            return PrettyPrinter.arrayAsString(charArray, 0, charArray.getLength(), 1024);
        }
    }

    @Subject("byte[]")
    public static class ByteArrayResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            byte dot = (byte) '.';
            IPrimitiveArray arr = (IPrimitiveArray) heapObject;
            byte[] value = (byte[]) arr.getContent(0, Math.min(arr.getLength(), 1024));
            for (int i = 0; i < value.length; i++)
            {
                if (value[i] < 32 || value[i] > 127)
                    value[i] = dot;
            }
            return new String(value);
        }
    }

}
