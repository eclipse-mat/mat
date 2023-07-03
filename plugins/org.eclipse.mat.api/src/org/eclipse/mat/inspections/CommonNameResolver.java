/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - additional resolvers and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObject.Type;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.PrettyPrinter;
import org.eclipse.mat.snapshot.registry.ClassSpecificNameResolverRegistry;

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

    @Subjects( { "java.lang.StringBuffer", //
                    "java.lang.StringBuilder" })
    public static class StringBufferResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            Integer count = (Integer) obj.resolveValue("count"); //$NON-NLS-1$
            if (count == null)
                return null;
            if (count == 0)
                return ""; //$NON-NLS-1$

            IPrimitiveArray charArray = (IPrimitiveArray) obj.resolveValue("value"); //$NON-NLS-1$
            if (charArray == null)
                return null;

            if (charArray.getType() == IObject.Type.BYTE)
            {
                // Java 9 compact strings
                return PrettyPrinter.objectAsString(obj, 1024);
            }
            else
            {
                return PrettyPrinter.arrayAsString(charArray, 0, count, 1024);
            }
        }
    }

    @Subject("java.lang.Thread")
    public static class ThreadResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            IObject name = (IObject) obj.resolveValue("name"); //$NON-NLS-1$
            return name != null ? name.getClassSpecificName() : null;
        }
    }

    @Subject("java.lang.ThreadGroup")
    public static class ThreadGroupResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject nameString = (IObject) object.resolveValue("name"); //$NON-NLS-1$
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
                    "java.lang.Boolean", //
                    "java.util.concurrent.atomic.AtomicInteger", //
                    "java.util.concurrent.atomic.AtomicLong", //
               })
    public static class ValueResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            Object value = heapObject.resolveValue("value"); //$NON-NLS-1$
            return value != null ? String.valueOf(value) : null;
        }
    }

    /**
     * For Oracle VMs for int.class, byte.class, void.class etc.
     * These are just simple IObjects, not IClass objects.
     * All other classes resolve via IClass.
     */
    @Subjects("java.lang.Class")
    public static class ClassTypeResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            // Let normal IClass resolution happen if possible
            if (object instanceof IClass)
                return null;
            IObject nameString = (IObject) object.resolveValue("name"); //$NON-NLS-1$
            if (nameString == null)
                return null;
            return nameString.getClassSpecificName();
        }
    }

    @Subjects("java.util.concurrent.atomic.AtomicBoolean")
    public static class AtomicBooleanResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            Integer value = (Integer) heapObject.resolveValue("value"); //$NON-NLS-1$
            return value != null ? Boolean.toString(value != 0) : null; 
        }
    }

    @Subjects("java.util.concurrent.atomic.AtomicReference")
    public static class AtomicReferenceValueResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IObject value = (IObject) heapObject.resolveValue("value"); //$NON-NLS-1$
            return value != null ? ClassSpecificNameResolverRegistry.resolve(value) : null; 
        }
    }

    @Subjects("java.util.concurrent.atomic.AtomicStampedReference")
    public static class AtomicStampedReferenceValueResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IObject value = (IObject) heapObject.resolveValue("pair.reference"); //$NON-NLS-1$
            return value != null ? ClassSpecificNameResolverRegistry.resolve(value) : null; 
        }
    }

    @Subject("char[]")
    public static class CharArrayResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IPrimitiveArray charArray = (IPrimitiveArray) heapObject;
            // Check for empty array
            int limit = 1024;
            Object v = charArray.getValueArray(0, Math.min(charArray.getLength(), limit));
            if (!(v instanceof char[]))
                return null;
            char[] value = (char[])v;

            int j;
            for (j = 0; j < value.length; j++)
            {
                if (value[j] != 0)
                    break;
            }
            if (j == value.length)
                return null;

            return PrettyPrinter.arrayAsString(charArray, 0, charArray.getLength(), limit);
        }
    }

    @Subject("byte[]")
    public static class ByteArrayResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IPrimitiveArray arr = (IPrimitiveArray) heapObject;
            // Check for empty array
            Object v = arr.getValueArray(0, Math.min(arr.getLength(), 1024));
            if (!(v instanceof byte[]))
                return null;
            byte[] value = (byte[])v;

            int j;
            for (j = 0; j < value.length; j++)
            {
                if (value[j] != 0)
                    break;
            }
            if (j == value.length)
                return null;

            // must not modify the original byte array
            StringBuilder r = new StringBuilder(value.length);
            for (int i = 0; i < value.length; i++)
            {
                // ASCII/Unicode 127 is not printable
                if (value[i] < 32 || value[i] > 126)
                    r.append('.');
                else
                    r.append((char) value[i]);
            }
            return r.toString();
        }
    }

    /*
     * Contributed in bug 273915
     */
    @Subject("java.net.URL")
    public static class URLResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            StringBuilder builder = new StringBuilder();
            IObject protocol = (IObject) obj.resolveValue("protocol"); //$NON-NLS-1$
            if (protocol != null)
            {
                builder.append(protocol.getClassSpecificName());
                builder.append(":"); //$NON-NLS-1$
            }
            IObject authority = (IObject) obj.resolveValue("authority"); //$NON-NLS-1$
            if (authority != null)
            {
                builder.append("//"); //$NON-NLS-1$
                builder.append(authority.getClassSpecificName());
            }
            IObject path = (IObject) obj.resolveValue("path"); //$NON-NLS-1$
            if (path != null)
                builder.append(path.getClassSpecificName());
            IObject query = (IObject) obj.resolveValue("query"); //$NON-NLS-1$
            if (query != null)
            {
                builder.append("?"); //$NON-NLS-1$
                builder.append(query.getClassSpecificName());
            }
            IObject ref = (IObject) obj.resolveValue("ref"); //$NON-NLS-1$
            if (ref != null)
            {
                builder.append("#"); //$NON-NLS-1$
                builder.append(ref.getClassSpecificName());
            }
            return builder.length() > 0 ? builder.toString() : null;
        }
    }

    @Subject("java.net.URI")
    public static class URIResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            StringBuilder builder = new StringBuilder();
            IObject string = (IObject) obj.resolveValue("string"); //$NON-NLS-1$
            if (string != null)
            {
                String val = string.getClassSpecificName();
                if (val != null)
                    return val;
            }
            IObject protocol = (IObject) obj.resolveValue("scheme"); //$NON-NLS-1$
            if (protocol != null)
            {
                builder.append(protocol.getClassSpecificName());
                builder.append(":"); //$NON-NLS-1$
            }
            IObject authority = (IObject) obj.resolveValue("authority"); //$NON-NLS-1$
            if (authority != null)
            {
                builder.append("//"); //$NON-NLS-1$
                builder.append(authority.getClassSpecificName());
            }
            IObject path = (IObject) obj.resolveValue("path"); //$NON-NLS-1$
            if (path != null)
                builder.append(path.getClassSpecificName());
            IObject query = (IObject) obj.resolveValue("query"); //$NON-NLS-1$
            if (query != null)
            {
                builder.append("?"); //$NON-NLS-1$
                builder.append(query.getClassSpecificName());
            }
            IObject ref = (IObject) obj.resolveValue("ref"); //$NON-NLS-1$
            if (ref != null)
            {
                builder.append("#"); //$NON-NLS-1$
                builder.append(ref.getClassSpecificName());
            }
            return builder.length() > 0 ? builder.toString() : null;
        }
    }

    @Subject("java.lang.reflect.AccessibleObject")
    public static class AccessibleObjectResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            // Important fields
            // modifiers - not actually present, but present in all superclasses
            // clazz - not actually present, but present in all superclasses
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers"); //$NON-NLS-1$
            if (val instanceof Integer)
            {
                r.append(Modifier.toString((Integer)val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
            }
            else
            {
                return null;
            }
            return r.toString();
        }

        protected void addClassName(ISnapshot snapshot, long addr, StringBuilder r) throws SnapshotException
        {
            ObjectReference or = new ObjectReference(snapshot, addr);
            IObject ox = or.getObject();
            if (ox instanceof IClass)
            {
                IClass cls = (IClass) ox;
                r.append(cls.getName());
            }
        }
    }
    
    @Subject("java.lang.reflect.Field")
    public static class FieldResolver extends AccessibleObjectResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            // Important fields
            // modifiers
            // clazz
            // name
            // type
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers"); //$NON-NLS-1$
            if (val instanceof Integer)
            {
                r.append(Modifier.toString((Integer)val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("type"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append('.');
            }
            ref = (IObject) obj.resolveValue("name"); //$NON-NLS-1$
            if (ref != null)
            {
                r.append(ref.getClassSpecificName());
            }
            else
            {
                // No method name so give up
                return null;
            }
            return r.toString();
        }
    }
    
    @Subject("java.lang.reflect.Method")
    public static class MethodResolver extends AccessibleObjectResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            // Important fields
            // modifiers
            // clazz
            // name
            // parameterTypes[]
            // exceptionTypes[]
            // returnType
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers"); //$NON-NLS-1$
            if (val instanceof Integer)
            {
                r.append(Modifier.toString((Integer)val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("returnType"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append('.');
            }
            ref = (IObject) obj.resolveValue("name"); //$NON-NLS-1$
            if (ref != null)
            {
                r.append(ref.getClassSpecificName());
            }
            else
            {
                // No method name so give up
                return null;
            }
            r.append('(');
            ref = (IObject) obj.resolveValue("parameterTypes"); //$NON-NLS-1$
            if (ref instanceof IObjectArray)
            {
                IObjectArray orefa = (IObjectArray) ref;
                long refs[] = orefa.getReferenceArray();
                for (int i = 0; i < orefa.getLength(); ++i)
                {
                    if (i > 0)
                        r.append(',');
                    long addr = refs[i];
                    addClassName(snapshot, addr, r);
                }
            }
            r.append(')');
            return r.toString();
        }
    }
    
    @Subject("java.lang.reflect.Constructor")
    public static class ConstructorResolver extends AccessibleObjectResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            // Important fields
            // modifiers
            // clazz
            // parameterTypes[]
            // exceptionTypes[]
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers"); //$NON-NLS-1$
            if (val instanceof Integer)
            {
                r.append(Modifier.toString((Integer)val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz"); //$NON-NLS-1$
            if (ref != null)
            {
                addClassName(snapshot, ref.getObjectAddress(), r);
            }
            else
            {
                // No class name so give up
                return null;
            }
            r.append('(');
            ref = (IObject) obj.resolveValue("parameterTypes"); //$NON-NLS-1$
            if (ref instanceof IObjectArray)
            {
                IObjectArray orefa = (IObjectArray) ref;
                long refs[] = orefa.getReferenceArray();
                for (int i = 0; i < orefa.getLength(); ++i)
                {
                    if (i > 0)
                        r.append(',');
                    long addr = refs[i];
                    addClassName(snapshot, addr, r);
                }
            }
            r.append(')');
            return r.toString();
        }
    }

    @Subject("java.lang.StackTraceElement")
    public static class StackTraceElementResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            IObject cls = (IObject)obj.resolveValue("declaringClass"); //$NON-NLS-1$
            IObject methodName = (IObject)obj.resolveValue("methodName"); //$NON-NLS-1$
            if (cls == null || methodName == null)
                return null;
            int line = (Integer)obj.resolveValue("lineNumber"); //$NON-NLS-1$
            IObject fn = (IObject)obj.resolveValue("fileName"); //$NON-NLS-1$
            String ln;
            if (line == -2)
                ln = "(Compiled Code)"; //$NON-NLS-1$
            else if (line == -3)
                ln = "(Native Method)"; //$NON-NLS-1$
            else if (line == -1)
                ln = ""; //$NON-NLS-1$
            else if (line == 0)
                ln = ""; //$NON-NLS-1$
            else
                ln = Integer.toString(line);
            String name;
            if (fn == null)
                if (line > 0)
                    name = cls.getClassSpecificName() + "." + methodName.getClassSpecificName() + "() " + ln; //$NON-NLS-1$ //$NON-NLS-2$
                else
                    name = cls.getClassSpecificName() + "." + methodName.getClassSpecificName() + "()";  //$NON-NLS-1$//$NON-NLS-2$
            else
                if (line > 0)
                    name = cls.getClassSpecificName() + "." + methodName.getClassSpecificName() + "() (" //$NON-NLS-1$ //$NON-NLS-2$
                                + fn.getClassSpecificName() + ":" + ln + ")";  //$NON-NLS-1$//$NON-NLS-2$
                else
                    name = cls.getClassSpecificName() + "." + methodName.getClassSpecificName() + "() (" //$NON-NLS-1$ //$NON-NLS-2$
                                    + fn.getClassSpecificName() + ")"; //$NON-NLS-1$
            return name;
        }
    }

    @Subject("java.lang.Enum")
    public static class EnumResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IObject value = (IObject) heapObject.resolveValue("name"); //$NON-NLS-1$
            return value != null ? ClassSpecificNameResolverRegistry.resolve(value) : null;
        }
    }

    @Subject("java.lang.ProcessEnvironment$ExternalData")
    public static class ExternalDataResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            IObject value = (IObject) heapObject.resolveValue("str"); //$NON-NLS-1$
            return value != null ? ClassSpecificNameResolverRegistry.resolve(value) : null;
        }
    }

    @Subject("java.lang.invoke.MemberName")
    public static class MemberNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject heapObject) throws SnapshotException
        {
            StringBuilder buf = new StringBuilder();
            IObject value = (IObject) heapObject.resolveValue("name"); //$NON-NLS-1$
            if (value != null)
            {
                IObject cvalue = (IObject) heapObject.resolveValue("clazz"); //$NON-NLS-1$
                if (cvalue instanceof IClass)
                {
                    IClass cls = (IClass)cvalue;
                    buf.append(cls.getName()).append('.');
                }
                buf.append(ClassSpecificNameResolverRegistry.resolve(value));
                return buf.toString();
            }
            return null;
        }
    }

    @Subject("java.net.InetAddress")
    public static class InetAddressResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            Object holder = object.resolveValue("holder"); //$NON-NLS-1$
            if (holder instanceof IObject)
                return ((IObject)holder).getClassSpecificName();
            else
                return null;
        }
    }

    @Subject("java.net.InetAddress$InetAddressHolder")
    public static class InetAddressHolderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            Object host = object.resolveValue("hostName"); //$NON-NLS-1$
            String hostname;
            if (host instanceof IObject)
            {
                hostname = ((IObject)host).getClassSpecificName();
            }
            else
            {
                hostname = null;
            }
            Object address = object.resolveValue("address"); //$NON-NLS-1$
            if (address instanceof Integer)
            {
                int addr = (Integer)address;
                byte b[] = new byte[] { (byte)(addr >>> 24),
                                (byte)(addr >>> 16),
                                (byte)(addr >>> 8),
                                (byte)(addr >>> 0)};
                try
                {
                    if (hostname != null)
                    {
                        InetAddress in = InetAddress.getByAddress(hostname, b);
                        return in.getHostName()+"/"+in.getHostAddress(); //$NON-NLS-1$
                    }
                    else
                    {
                        InetAddress in = InetAddress.getByAddress(b);
                        return in.getHostAddress();
                    }
                }
                catch (UnknownHostException e)
                {
                }
            }
            else if (hostname != null)
            {
                return hostname;
            }
            return null;
        }
    }


    @Subject("java.net.Inet6Address")
    public static class Inet6AddressResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            Object holder = object.resolveValue("holder6"); //$NON-NLS-1$
            if (holder instanceof IObject)
                return ((IObject)holder).getClassSpecificName();
            else
                return null;
        }
    }

    @Subject("java.net.Inet6Address$Inet6AddressHolder")
    public static class Inet6AddressHolderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            Object host = object.resolveValue("hostName"); //$NON-NLS-1$
            String hostname;
            if (host instanceof IObject)
            {
                hostname = ((IObject)host).getClassSpecificName();
            }
            else
            {
                hostname = null;
            }
            Object address = object.resolveValue("ipaddress"); //$NON-NLS-1$
            if (address instanceof IPrimitiveArray)
            {
                IPrimitiveArray p = (IPrimitiveArray)address;
                Object vals = p.getValueArray();
                if (vals instanceof byte[])
                {
                    byte b[] = (byte[])vals;
                    try
                    {
                        if (hostname != null)
                        {
                            Object os = object.resolveValue("scope_id_set"); //$NON-NLS-1$
                            Object oid = object.resolveValue("scope_id"); //$NON-NLS-1$
                            if (os instanceof Boolean && (Boolean)os && oid instanceof Integer)
                            {
                                InetAddress in = Inet6Address.getByAddress(hostname, b, (Integer)oid);
                                return in.getHostName()+"/" +in.getHostAddress(); //$NON-NLS-1$
                            }
                            else
                            {
                                InetAddress in = InetAddress.getByAddress(hostname, b);
                                return in.getHostName()+"/"+in.getHostAddress(); //$NON-NLS-1$
                            }
                        }
                        else
                        {
                            InetAddress in = InetAddress.getByAddress(b);
                            return in.getHostAddress();
                        }
                    }
                    catch (UnknownHostException e)
                    {
                    }
                }
            }
            else if (hostname != null)
            {
                return hostname;
            }
            return null;
        }
    }

    @Subject("java.net.InetSocketAddress$InetSocketAddressHolder")
    public static class InetSocketAddressHolderResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            StringBuilder res = new StringBuilder();
            Object addr = object.resolveValue("addr"); //$NON-NLS-1$
            String addrname = (addr instanceof IObject) ? ((IObject) addr).getClassSpecificName() : null;

            Object host = object.resolveValue("host"); //$NON-NLS-1$
            String hostname = (host instanceof IObject) ? ((IObject) host).getClassSpecificName() : null;

            if (addrname != null && addrname.indexOf('/') >= 0)
            {
                int slash = addrname.indexOf('/');
                if (addrname.substring(slash + 1).indexOf(':') >= 0)
                {
                    // IPv6
                    res.append(addrname.substring(0, slash + 1));
                    res.append('[');
                    res.append(addrname.substring(slash + 1));
                    res.append(']');
                }
                else
                {
                    res.append(addrname);
                }
            }
            else if (hostname != null)
            {
                res.append(hostname);
                if (addrname != null)
                {
                    res.append('/');
                    if (addrname.indexOf(':') >= 0)
                        res.append('[').append(addrname).append(']');
                    else
                        res.append(addrname);
                }
            }
            else if (addrname != null)
            {
                if (addrname.indexOf(':') >= 0)
                    res.append('[').append(addrname).append(']');
                else
                    res.append(addrname);
            }
            res.append(':');
            Object port = object.resolveValue("port"); //$NON-NLS-1$
            if (port instanceof Integer)
            {
                res.append(port);
            }
            if (res.length() > 1)
                return res.toString();
            else
                return null;
        }
    }

    @Subject("java.net.InetSocketAddress")
    public static class InetSocketAddressResolver implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            Object holder = object.resolveValue("holder"); //$NON-NLS-1$
            if (holder instanceof IObject)
            {
                return ((IObject)holder).getClassSpecificName();
            }
            else
                return null;
        }
    }
    
    
    @Subject("sun.nio.ch.SocketChannelImpl")
    public static class SocketChannelImpl implements IClassSpecificNameResolver
    {

        public String resolve(IObject object) throws SnapshotException
        {
            StringBuilder sb = new StringBuilder();
            Object state = object.resolveValue("state"); //$NON-NLS-1$
            if (state instanceof Integer)
            {
                IClass cls = object.getClazz();
                Object s1 = cls.resolveValue("ST_UNINITIALIZED"); //$NON-NLS-1$
                Object s2 = cls.resolveValue("ST_UNCONNECTED"); //$NON-NLS-1$
                Object s3 = cls.resolveValue("ST_PENDING"); //$NON-NLS-1$
                Object s4 = cls.resolveValue("ST_CONNECTED"); //$NON-NLS-1$
                Object s5 = cls.resolveValue("ST_KILLPENDING"); //$NON-NLS-1$
                Object s6 = cls.resolveValue("ST_KILLED"); //$NON-NLS-1$
                
                if (state.equals(s1))
                    sb.append("uninitialized"); //$NON-NLS-1$
                else if (state.equals(s2))
                {
                     sb.append("unconnected"); //$NON-NLS-1$
                }
                else if (state.equals(s3))
                {
                     sb.append("pending"); //$NON-NLS-1$
                }
                else if (state.equals(s4))
                {
                     sb.append("connected"); //$NON-NLS-1$
                }
                else if (state.equals(s5))
                {
                     sb.append("kill pending"); //$NON-NLS-1$
                }
                else if (state.equals(s6))
                {
                     sb.append("killed"); //$NON-NLS-1$
                }
                if (sb.length() > 0)
                    sb.append(' ');
            }
            sb.append("local="); //$NON-NLS-1$
            Object localAddress = object.resolveValue("localAddress");  //$NON-NLS-1$
            if (localAddress instanceof IObject)
            {
                String v = ((IObject)localAddress).getClassSpecificName();
                if (v != null)
                    sb.append(v);
            }
            sb.append(" remoteAddress="); //$NON-NLS-1$
            Object remoteAddress = object.resolveValue("remoteAddress");  //$NON-NLS-1$
            if (remoteAddress instanceof IObject)
            {
                String v = ((IObject)remoteAddress).getClassSpecificName();
                if (v != null)
                    sb.append(v);
            }
            return sb.toString();
        }
    }

    @Subjects({"java.lang.Module","java.lang.Package"})
    public static class ModuleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            IObject name = (IObject) obj.resolveValue("name"); //$NON-NLS-1$
            return name != null ? name.getClassSpecificName() : null;
        }
    }

    private static String resolveField(IObject obj, String field) throws SnapshotException
    {
        IObject fieldObject = (IObject) obj.resolveValue(field);
        return fieldObject != null ? fieldObject.getClassSpecificName() : null;
    }

    @Subjects({"java.util.Locale"})
    public static class LocaleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            String languageTag = resolveField(obj, "languageTag"); //$NON-NLS-1$
            if (languageTag != null && !languageTag.isEmpty())
                return languageTag;
            return resolveField(obj, "baseLocale"); //$NON-NLS-1$
        }
    }

    @Subjects({"sun.util.locale.BaseLocale"})
    public static class SunLocaleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            String lang = resolveField(obj, "language"); //$NON-NLS-1$
            String region = resolveField(obj, "region"); //$NON-NLS-1$
            String script = resolveField(obj, "script"); //$NON-NLS-1$
            String variant = resolveField(obj, "variant"); //$NON-NLS-1$
            StringBuilder result = new StringBuilder();
            if (lang != null)
                result.append(lang);
            if (script != null && !script.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(script);
            }
            if (region != null && !region.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(region);
            }
            if (variant != null && !variant.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(variant);
            }
            return result.toString();
        }
    }

    @Subjects({"com.ibm.icu.impl.locale.BaseLocale"})
    public static class ICUBaseLocaleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            String lang = resolveField(obj, "_language"); //$NON-NLS-1$
            String region = resolveField(obj, "_region"); //$NON-NLS-1$
            String script = resolveField(obj, "_script"); //$NON-NLS-1$
            String variant = resolveField(obj, "_variant"); //$NON-NLS-1$
            StringBuilder result = new StringBuilder();
            if (lang != null)
                result.append(lang);
            if (script != null && !script.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(script);
            }
            if (region != null && !region.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(region);
            }
            if (variant != null && !variant.isEmpty())
            {
                if (result.length() > 0)
                    result.append('_');
                result.append(variant);
            }
            return result.toString();
        }
    }

    @Subjects({"com.ibm.icu.util.ULocale"})
    public static class ULocaleResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            String localeID = resolveField(obj, "localeID"); //$NON-NLS-1$
            if (localeID != null && !localeID.isEmpty())
                return localeID;
            String locale = resolveField(obj, "locale"); //$NON-NLS-1$
            if (locale != null && !locale.isEmpty())
                return locale;
             String baseLocale = resolveField(obj, "baseLocale"); //$NON-NLS-1$
            if (baseLocale != null && !baseLocale.isEmpty())
                return locale;
            return null;
        }
    }

    @Subject("java.math.BigInteger")
    public static class BigIntegerResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            boolean little = false;
            Object sn = obj.resolveValue("sign"); //$NON-NLS-1$
            if (!(sn instanceof Integer))
            {
                sn = obj.resolveValue("signum"); //$NON-NLS-1$
                if (!(sn instanceof Integer))
                {
                    return null;
                }
            }
            Object mg = obj.resolveValue("mag"); //$NON-NLS-1$
            if (!(mg instanceof IPrimitiveArray))
            {
                mg = obj.resolveValue("digits"); //$NON-NLS-1$
                if (!(mg instanceof IPrimitiveArray))
                    return null;
                little = true;
            }
            int signum = (Integer)sn;
            IPrimitiveArray pa = (IPrimitiveArray)mg;
            if (pa.getType() != Type.INT)
                return null;
            int mag[] = (int[])pa.getValueArray();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DataOutputStream dos = new DataOutputStream(baos))
            {
                if (little)
                {
                    for (int i = mag.length - 1; i >= 0; --i)
                    {
                        dos.writeInt(mag[i]);
                    }
                }
                else
                {
                    for (int m : mag)
                    {
                        dos.writeInt(m);
                    }
                }
                dos.flush();
                BigInteger bi = new BigInteger(signum, baos.toByteArray());
                return bi.toString();
            }
            catch (IOException e)
            {
                return null;
            }
        }
    }

    @Subject("java.math.BigDecimal")
    public static class BigDecimalResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject obj) throws SnapshotException
        {
            // Find the number
            // OpenJDK & Harmony
            Object iv = obj.resolveValue("intVal"); //$NON-NLS-1$
            String num = null;
            if (iv instanceof IObject)
            {
                IObject io = (IObject) iv;
                num = io.getClassSpecificName();
            }
            else
            {
                // OpenJDK
                iv = obj.resolveValue("intCompact"); //$NON-NLS-1$
                // Harmony
                if (iv == null)
                    iv = obj.resolveValue("smallValue"); //$NON-NLS-1$
                // 1.4.2
                if (iv == null)
                    iv = obj.resolveValue("intLong"); //$NON-NLS-1$
                if (iv instanceof Number)
                {
                    // Not applicable value?
                    if (((Number) iv).longValue() == Long.MIN_VALUE)
                        return null;
                    num = ((Number) iv).toString();
                }
            }
            if (num == null)
                return null;
            // Find the scale and precision
            int scale = 0;
            // OpenJDK & Harmony
            Object scaleo = obj.resolveValue("scale"); //$NON-NLS-1$
            if (scaleo instanceof Integer)
            {
                scale = (Integer) scaleo;
            }
            // OpenJDK & Harmony
            Object precisiono = obj.resolveValue("precision"); //$NON-NLS-1$
            BigDecimal bd;
            if (precisiono instanceof Integer)
            {
                int precision = (Integer) precisiono;
                MathContext mc = new MathContext(precision);
                if (scale != 0)
                {
                    bd = new BigDecimal(new BigInteger(num), scale, mc);
                }
                else
                {
                    bd = new BigDecimal(num, mc);
                }
            }
            else
            {
                if (scale != 0)
                {
                    bd = new BigDecimal(new BigInteger(num), scale);
                }
                else
                {
                    bd = new BigDecimal(num);
                }
            }
            return bd.toPlainString();
        }
    }
}
