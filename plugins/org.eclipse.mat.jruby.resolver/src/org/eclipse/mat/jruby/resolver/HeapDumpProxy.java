/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dimitar Giormov - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.jruby.resolver;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.osgi.util.NLS;
import org.jruby.javasupport.JavaObject;

/**
 * A utility for creating compact and error-checking proxy objects for accessing the state of
 * individual objects in a heap dump.
 * 
 * @author Dimitar Giormov
 */
public class HeapDumpProxy {

    /**
     * Creates an implementation of the given interface which extracts the field values for the given
     * heap dump object, which should be of the given type. Each method in the interface is mapped as
     * a getter of the corresponding field on the class with the same name and type.
     * <p>
     * This allows much more compact, readable and verified access to the heap dump state.
     * 
     * @param <T>
     * @param fakeInterface
     * @param realClass
     * @param heapDumpObject
     * @return T
     */
    public static <T> T make(Class<T> fakeInterface, Class<?> realClass, IObject heapDumpObject) {
        return make(fakeInterface.getClassLoader(), fakeInterface, realClass, heapDumpObject);
    }
    
    /**
     * Internal version of {@link #make(Class, Class, JavaObject)} that passes along the original
     * class loader so as not to lose context as we traverse the object graph in the heap dump.
     * @throws ClassNotFoundException 
     */
    private static <T> T make(ClassLoader loader, Class<T> fakeInterface, Class<?> realClass, IObject heapDumpObject) {
        // Support converting directly to a java.lang.String even though it's not a primitive, 
        // since it's so common.
        if (fakeInterface.equals(String.class)) {
            ProxyString proxyString = make(loader, ProxyString.class, realClass, heapDumpObject);
            // Safe cast, since the if above has guaranteed that fakeInterface is of type Class<String>,
            // so we know T == String.
            return unsafeCast(new String(proxyString.value(), proxyString.offset(), proxyString.count()));
        } else {
            // Check that the types are consistent between the current code base and the heap dump.
            Class<?> heapDumpClass;
            try {
                heapDumpClass = Class.forName(heapDumpObject.getClazz().getName(), false, loader);
            } catch (ClassNotFoundException e) {
               throw new IllegalArgumentException(NLS.bind(Messages.HeapDumpProxy_UnknownClass, heapDumpObject.getClazz().getName()), e); 
            }
            if (!realClass.isAssignableFrom(heapDumpClass)) {
                throw new IllegalArgumentException(NLS.bind(Messages.HeapDumpProxy_TypeMismatch, heapDumpClass, realClass));
            }
            
            Object proxy = Proxy.newProxyInstance(
                    loader, 
                    new Class<?>[] { fakeInterface }, 
                    new Handler(loader, realClass, heapDumpObject));
            return fakeInterface.cast(proxy);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T unsafeCast(Object obj) {
        return (T) obj;
    }
    
    /**
     * Invocation handler for a heap dump proxy.
     * 
     * @author Robin Salkeld
     */
    public static class Handler implements InvocationHandler {

        /** The class loader to use in constructing child proxy objects. */
        private final ClassLoader loader;
        
        /** 
         * The class of the object in the heap dump. Technically not necessary for extracting values,
         * but helps to validate that the heap dump is compatible with the current code base.
         * We'll raise errors if the proxy interface doesn't match the fields declared on this class. 
         */
        private final Class<?> realClass;
        
        private final IObject heapDumpObject;
        
        /**
         * Constructor for Handler
         * @param loader
         * @param realClass
         * @param heapDumpObject
         */
        public Handler(final ClassLoader loader, final Class<?> realClass, final IObject heapDumpObject) {
            this.loader = loader;
            this.realClass = realClass;
            this.heapDumpObject = heapDumpObject;
        }
        
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            
            // Forward equals() and hashCode() to the heap dump object, so that we can identify
            // those objects using their proxies (e.g. in Maps).
            if (methodName.equals("equals")) { //$NON-NLS-1$
                Object other = args[0];
                if (Proxy.isProxyClass(other.getClass())) {
                    return equals(Proxy.getInvocationHandler(proxy));
                } else {
                    return false;
                }
            } else if (methodName.equals("hashCode")) { //$NON-NLS-1$
                return hashCode();
            }
            
            final Field field;
            try {
                field = realClass.getDeclaredField(methodName);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(NLS.bind(Messages.HeapDumpProxy_NoSuchField, realClass.getName(), methodName)); 
            }
            final Class<?> fieldType = field.getType();
            
            final Object fieldValue = heapDumpObject.resolveValue(methodName);
            
            // This seems to be the way null is represented by the hat tool.
            // Shouldn't happen for primitive fields - if it does the Proxy layer will complain.
            if (fieldValue == null) {
                return null;
            }

            if (fieldType.isPrimitive()) {
                // Case 1: primitive values. Convert directly using one of the primitive wrappers.
                if (fieldType.equals(Integer.TYPE)) {
                    return Integer.parseInt(((Integer)fieldValue).toString());
                } else if (fieldType.equals(Boolean.TYPE)) {
                    return Boolean.parseBoolean(((Boolean)fieldValue).toString());
                } else {
                    throw new UnsupportedOperationException(Messages.HeapDumpProxy_NotImplemented); 
                }
            } else if (fieldType.isArray()) {
                if (fieldType.getComponentType().isPrimitive()) {
                	return ((IObject)fieldValue).getClassSpecificName().toCharArray();
                } else {
                    // Case 3: Array of objects. Create a child proxy for each element
                    // based on the declared array return type in the interface.
                    final IObjectArray fieldValueArray = (IObjectArray)fieldValue;
                    final long[] elements = fieldValueArray.getReferenceArray();
                    final Class<?> fieldElementType = fieldType.getComponentType();
                    ISnapshot snapshot = fieldValueArray.getSnapshot();
                    final Class<?> returnElementType = method.getReturnType().getComponentType();
                    final Object[] returnArray = (Object[])Array.newInstance(returnElementType, elements.length);
                    for (int i = 0; i < elements.length; i++) {
                    	int mapAddressToId = snapshot.mapAddressToId(elements[i]);
                        returnArray[i] = make(loader, returnElementType, fieldElementType, snapshot.getObject(mapAddressToId));
                    }
                    return returnArray;
                }
            } else {
                // Case 4: Regular object. Create a child proxy based on the 
                // declared return type in the interface.
                final IObject returnObject = (IObject)fieldValue;
                final Class<?> returnType = method.getReturnType();
//
                return HeapDumpProxy.make(loader, returnType, fieldType, returnObject);
            }
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Handler)) {
                return false;
            } else {
                Handler other = (Handler)obj;
                return heapDumpObject.equals(other.heapDumpObject);
            }
        }
        
        @Override
        public int hashCode() {
            return 47 * heapDumpObject.hashCode();
        }
    }
    
    /** 
     * Proxy interface for java.lang.String. Used as a convenience for converting Strings directly 
     */
    public static interface ProxyString {
        char[] value();
        int offset();
        int count();
    }
}
