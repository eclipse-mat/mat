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
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

public class CommonNameResolver
{
    @Subject("java.lang.String")
    public static class StringResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject obj) throws SnapshotException
        {
            IInstance s = (IInstance) obj;
            // some string (when caught in constructor?) do not reference a
            // char array
            int count = (Integer) s.resolveValue("count");
            if (count == 0)
                return "";

            IPrimitiveArray charArray = (IPrimitiveArray) s.resolveValue("value");
            if (charArray == null)
                return null;

            int offset = (Integer) s.resolveValue("offset");

            return charArray.valueString(1024, offset, count);
        }

    }

    @Subject("java.lang.Thread")
    public static class ThreadResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            IInstance s = (IInstance) obj;
            IPrimitiveArray charArray = (IPrimitiveArray) s.resolveValue("name");
            return charArray != null ? charArray.valueString(1024) : null;
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
            return ((IPrimitiveArray) heapObject).valueString(1024);
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
