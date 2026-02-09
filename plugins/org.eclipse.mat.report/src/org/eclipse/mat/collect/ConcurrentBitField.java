/*******************************************************************************
 * Copyright (c) 2008, 2024 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Jason Koch (Netflix, Inc) - implementation
 *******************************************************************************/
package org.eclipse.mat.collect;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * This class manages huge bit fields. It is much faster than
 * {@link java.util.BitSet} and was specifically developed to be used with huge
 * bit sets in ISnapshot (e.g. needed in virtual GC traces). Out of performance
 * reasons no method does any parameter checking, i.e. only valid values are
 * expected. This is a fully thread-safe/concurrent implementation.
 */
public final class ConcurrentBitField
{

    private final AtomicLongArray bits;
    private final int size;

    private static final int SHIFT = 0x6;
    private static final int MASK = 0x3f;

    /**
     * Creates a bit field with the given number of bits. Size is expected to be
     * positive.
     * @param size the maximum size of the BitField
     */
    public ConcurrentBitField(int size)
    {
        if (size <= 0)
        {
            throw new IllegalArgumentException("size must be > 0");
        }
        this.size = size;
        this.bits = new AtomicLongArray((((size) - 1) >>> SHIFT) + 1);
    }

    public ConcurrentBitField(boolean[] bits)
    {
        if (bits.length == 0)
        {
            throw new IllegalArgumentException("bits must have at least one element");
        }
        this.size = bits.length;
        this.bits = new AtomicLongArray((((size) - 1) >>> SHIFT) + 1);

        for (int i = 0; i < size; i++)
        {
            if (bits[i])
                set(i);
        }
    }

    /**
     * Sets the bit on the given index. Index is expected to be in range - out
     * of performance reasons no checks are done!
     * @param index The 0-based index into the BitField.
     */
    public final void set(int index)
    {
        final int slot = index >>> SHIFT;
        final long flag = (1L << (index & MASK));

        while (true)
        {
            final long existing = bits.get(slot);
            final long next = existing | flag;
            if (next == existing)
            { return; }

            if (bits.compareAndSet(slot, existing, next))
            { return; }
        }
    }

    /**
     * Clears the bit on the given index. Index is expected to be in range - out
     * of performance reasons no checks are done!
     * @param index The 0-based index into the BitField.
     */
    public final void clear(int index)
    {
        final int slot = index >>> SHIFT;
        final long flag = (1L << (index & MASK));

        while (true)
        {
            final long existing = bits.get(slot);
            final long next = existing & (~flag);
            if (next == existing)
            { return; }

            if (bits.compareAndSet(slot, existing, next))
            { return; }
        }
    }

    /**
     * Compare and set the value atomically. NB multiple underlying CAS
     * might be competing, but only once ever for the same bit.
     * @param index
     * @return true if successful. False return indicates that the actual value
     * was not equal to the expected value.
     */
    public final boolean compareAndSet(int index, boolean expectedValue, boolean newValue) {
        int slot = index >>> SHIFT;
        long flag = (1L << (index & MASK));

        // We need to do a two-pass here
        // Load the value, then update the mask, and if then attempt a CAS
        // there are two possibilities for CAS failure:
        // (1) someone changed the flag we are interested in
        // (2) someone changed a different flag in the block
        // Therefore, we need to use full compare and exchange rather than
        // compare and set.

        while (true)
        {
            final long existing = bits.get(slot);
            final boolean currentBit = (existing & flag) != 0;

            // expected bit does not match
            if (currentBit != expectedValue)
            {
                return false;
            }

            final long nextValue = newValue
                ? (existing | flag)
                : (existing & ~flag);

            // we know that expected matches, and now next matches, then done
            if (nextValue == existing)
            {
                return true;
            }

            final long witness = bits.compareAndExchange(slot, existing, nextValue);

            // cas succeeded
            if (witness == existing)
            {
                return true;
            }

            // cas failed, but why?
            // check the returned value, and, if it was changed by someone else
            // the CAS fails
            boolean witnessBit = (witness & flag) != 0L;
            if (witnessBit != expectedValue)
            {
                return false;
            }

            // otherwise, we know that the bit was OK but some other bits in the
            // slot changed we can safely retry
        }
    }

    /**
     * Gets the bit on the given index. Index is expected to be in range - out
     * of performance reasons no checks are done!
     * @param index The 0-based index into the BitField.
     * @return true if the BitField was set, false if it was cleared or never set.
     */
    public final boolean get(int index) {
        final int slot = index >>> SHIFT;
        final long flag = (1L << (index & MASK));

        final long existing = bits.get(slot);
        return (existing & flag) != 0;
    }

    /**
     * The size of the bitfield.
     * @return
     */
    public final int size() {
        return this.size;
    }

    /**
     * Gets the full array. Note that this is _not_ a thread-safe snapshot.
     * @return
     */
    public final boolean[] toBooleanArrayNonAtomic() {
        final boolean[] result = new boolean[size];
        intoBooleanArrayNonAtomic(result);
        return result;
    }

    /**
     * Gets the full array. Note that this is _not_ a thread-safe snapshot.
     * @param output array to fill
     */
    public final void intoBooleanArrayNonAtomic(boolean[] output) {
        if (output.length != size)
        {
            throw new IllegalArgumentException("output length must match bitset length");
        }
        for (int i = 0; i < size; i++)
        {
            output[i] = get(i);
        }
    }
}
