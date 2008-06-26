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
package org.eclipse.mat.snapshot.model;

/**
 * Interface for primitive arrays in the heap dump.
 * 
 * @noimplement
 */
public interface IPrimitiveArray extends IArray
{
    /**
     * Primitive signatures.
     */
    public static final byte[] SIGNATURES = { -1, -1, -1, -1, (byte) 'Z', (byte) 'C', (byte) 'F', (byte) 'D',
                    (byte) 'B', (byte) 'S', (byte) 'I', (byte) 'J' };

    /**
     * Element sizes inside the array.
     */
    public static final int[] ELEMENT_SIZE = { -1, -1, -1, -1, 1, 2, 4, 8, 1, 2, 4, 8 };

    /**
     * Display string of the type.
     */
    public static final String[] TYPE = { null, null, null, null, "boolean[]", "char[]", "float[]", "double[]",
                    "byte[]", "short[]", "int[]", "long[]" };

    /**
     * Returns the {@link Type} of the primitive array.
     */
    public int getType();

    /**
     * Returns the Object at a given index.
     */
    public Object getValueAt(int index);

}
