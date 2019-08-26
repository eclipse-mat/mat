/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson (IBM Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests;

/**
 * Creates many arrays and initializes them.
 * Used to stress test object marking, HPROF parsing,
 * and dominator tree building.
 *
 */
public class CreatePerformanceDump
{
    public static final int DEFAULT_M = 200000;
    public static final int DEFAULT_N = 200;
    final String a[][];
    final int numStrings;

    /**
     * Create lots of arrays of Strings.
     * @param m size of each sub-array
     * @param n number of sub-arrays
     * @param mode How to initialize the arrays.
     *  0 first sub-array is initalized with different strings, others use same strings
     *  1 first sub-array is initalized with different strings, others use strings from
     *    first sub-array or sometimes new copies of the string
     *  2 first sub-array is initalized with different strings, others use strings from
     *    the previous sub-array or sometimes new copies of the string
     *  3 first sub-array is initalized with different strings, others use strings from
     *    previous sub-arrays or sometimes new copies of the string
     *  4 sub-arrays filled with just one string
     *  
     *  Mode 0 creates m strings
     *  Modes 1,2,3 create m + m*(n-1)/n strings = m*(2-1/n) strings
     *  Mode 4 creates a single string
     */
    public CreatePerformanceDump(int m, int n, int mode) {
        a = new String[n][m];
        int s = 0;
        for (int i = 0; i < m; ++i)
        {
            if (mode == 4 && i > 0)
            {
                a[0][i] = a[0][i - 1];
            }
            else
            {
                a[0][i] = "S"+i;
                ++s;
            }
        }
        for (int j = 1; j < n; ++j)
        {
            for (int i = 0; i < m; ++i)
            {
                // fill in the array entries
                int prev = 0;
                if (mode == 2)
                    prev = j - 1;
                else if (mode == 3)
                    prev = (int)((long)i * j / m);
                
                if (i % n == j && mode > 0 && mode != 4)
                {
                    // sometimes with brand new Strings
                    a[j][i] = new String(a[prev][i]);
                    ++s;
                }
                else
                {
                    a[j][i] = a[prev][i];
                }
            }
        }
        numStrings = s;
   }

    public static void main(String[] args) throws Exception
    {
        int m = args.length > 0 ? Integer.parseInt( args[0]) : DEFAULT_M;
        int n = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_N;
        int mode = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        CreatePerformanceDump b = new CreatePerformanceDump(m, n, mode);
        System.out.println("Acquire Heap Dump NOW (then press any key to terminate program)");
        int c = System.in.read();
        // Control-break causes read to return early, so try again for another key to wait
        // for the dump to complete
        if (c == -1)
            c = System.in.read();

        System.out.println("Created array of size " + n + " with each entry holding an array of size " + m
                        + " of String with " + b.numStrings + " different strings using mode: " + mode
                        + ". Last entry: " + b.a[b.a.length - 1][b.a[b.a.length - 1].length - 1]);
        }
}
