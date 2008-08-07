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
package org.eclipse.mat.tests.collect;

import java.util.Random;

import org.eclipse.mat.collect.ArrayIntCompressed;
import org.eclipse.mat.collect.ArrayLongCompressed;
import org.junit.Test;

public class CompressedArraysTest
{
    @Test
    public void testIntArrayCompressed()
    {
        Random rand = new Random();
        int INTS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= INTS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                int[] ints = new int[i];
                for (int k = 0; k < ints.length; k++)
                    ints[k] = rand.nextInt() & 0x00ff7ff8;

                ArrayIntCompressed array = new ArrayIntCompressed(ints);
                byte[] bytes = array.toByteArray();
                array = new ArrayIntCompressed(bytes);
                for (int k = 0; k < ints.length; k++)
                {
                    if (ints[k] != array.get(k))
                        throw new RuntimeException("Failure at " + k + ". element! Expected was " + ints[k]
                                        + " Found was " + array.get(k) + "!");
                }
            }
        }
    }

    @Test
    public void testLongArrayCompressed()
    {
        Random rand = new Random();
        int LONGS = 1024;
        int TESTS = 10;
        for (int i = 1; i <= LONGS; i++)
        {
            for (int j = 0; j < TESTS; j++)
            {
                long[] longs = new long[i];
                for (int k = 0; k < longs.length; k++)
                    longs[k] = rand.nextLong() & 0x00ffffffffff7ff8L;

                ArrayLongCompressed array = new ArrayLongCompressed(longs);
                byte[] bytes = array.toByteArray();
                array = new ArrayLongCompressed(bytes);
                for (int k = 0; k < longs.length; k++)
                {
                    if (longs[k] != array.get(k))
                        throw new RuntimeException("Failure at " + k + ". element! Expected was " + longs[k]
                                        + " Found was " + array.get(k) + "!");
                }
            }
        }
    }
}
