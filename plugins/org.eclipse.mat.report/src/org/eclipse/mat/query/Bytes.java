/*******************************************************************************
 * Copyright (c) 2014,2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation
 *******************************************************************************/
package org.eclipse.mat.query;

/**
 * Logical representation of a number of bytes. This class is immutable, so
 * operations such as add will return new instances.
 * 
 * @since 1.5
 */
public final class Bytes implements Comparable<Object>
{
    private final long value;

    /**
     * Create an immutable instance of a logical representation of a number of
     * {@code bytes}.
     * 
     * @param bytes
     *            The number of bytes to represent.
     */
    public Bytes(final long bytes)
    {
        value = bytes;
    }

    /**
     * Get the underlying number of bytes as a long.
     * 
     * @return The underlying number of bytes as a long.
     */
    public long getValue()
    {
        return value;
    }

    /**
     * @see java.lang.Long#toString()
     */
    @Override
    public String toString()
    {
        return Long.toString(value);
    }

    /**
     * @see java.lang.Long#hashCode()
     */
    @Override
    public int hashCode()
    {
        return Long.hashCode(value);
    }

    /**
     * If comparing to another instances of {@link org.eclipse.mat.query.Bytes},
     * return true if the results of {@link #getValue()} are the same.
     */
    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Bytes)
        {
            Bytes y = (Bytes) o;
            return value == y.value;
        }
        return super.equals(o);
    }

    /**
     * Add a number of bytes to the current value from {@link #getValue()} and
     * return a new instance of {@link org.eclipse.mat.query.Bytes}.
     * 
     * @param add
     *            The amount of bytes to add.
     * @return A new instance of {@link org.eclipse.mat.query.Bytes} with the
     *         previous value summed with {@code add}.
     */
    public Bytes add(long add)
    {
        return new Bytes(getValue() + add);
    }

    /**
     * If comparing to another instances of {@link org.eclipse.mat.query.Bytes},
     * compare the values returned by {@link #getValue()}.
     */
    public int compareTo(Object y)
    {
        if (y instanceof Bytes)
        {
            Bytes by = (Bytes) y;
            if (value == by.value)
            {
                return 0;
            }
            else if (value > by.value)
            {
                return 1;
            }
            else
            {
                return -1;
            }
        }
        return 0;
    }
}
