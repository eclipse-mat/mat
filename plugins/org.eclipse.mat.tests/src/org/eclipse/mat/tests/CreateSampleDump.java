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
package org.eclipse.mat.tests;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class CreateSampleDump
{

    public static void main(String[] args) throws Exception
    {
        DominatorTestData dominatorTestData = new DominatorTestData();

        ReferenceTestData referenceTestData = new ReferenceTestData();

        System.out.println("Acquire Heap Dump NOW (then press any key to terminate program)");
        System.in.read();

        System.out.println(dominatorTestData);
        System.out.println(referenceTestData);
    }

    // //////////////////////////////////////////////////////////////
    // dominator tree
    // //////////////////////////////////////////////////////////////

    static class DominatorTestData
    {
        R r;

        DominatorTestData()
        {
            // Create Lengauer & Tarjan Paper Tree
            A a = new A();
            B b = new B();
            C c = new C();
            D d = new D();
            E e = new E();
            F f = new F();
            G g = new G();
            H h = new H();
            I i = new I();
            J j = new J();
            K k = new K();
            L l = new L();
            r = new R();
            a.d = d;
            b.a = a;
            b.d = d;
            b.e = e;
            c.f = f;
            c.g = g;
            d.l = l;
            e.h = h;
            f.i = i;
            g.i = i;
            g.j = j;
            h.e = e;
            h.k = k;
            i.k = k;
            j.i = i;
            k.i = i;
            k.r = r;
            l.h = h;
            r.a = a;
            r.b = b;
            r.c = c;
        }

        static class A
        {
            D d;
        }

        static class B
        {
            A a;
            D d;
            E e;
        }

        static class C
        {
            F f;
            G g;
        }

        static class D
        {
            L l;
        }

        static class E
        {
            H h;
        }

        static class F
        {
            I i;
        }

        static class G
        {
            I i;
            J j;
        }

        static class H
        {
            E e;
            K k;
        }

        static class I
        {
            K k;
        }

        static class J
        {
            I i;
        }

        static class K
        {
            I i;
            R r;
        }

        static class L
        {
            H h;
        }

        static class R
        {
            A a;
            B b;
            C c;
        }
    }

    // //////////////////////////////////////////////////////////////
    // paths tests
    // //////////////////////////////////////////////////////////////

    static class ReferenceTestData
    {
        @SuppressWarnings("unchecked")
        SoftReference clearedSoftReference;
        @SuppressWarnings("unchecked")
        SoftReference availableSoftReference;
        @SuppressWarnings("unchecked")
        WeakReference clearedWeakReference;
        @SuppressWarnings("unchecked")
        WeakReference availableWeakReference;
        String keptWeakReference;

        ReferenceTestData()
        {
            // Soft reference
            int size = (int) Runtime.getRuntime().totalMemory();
            byte[] lostSoftReference = null;
            while (lostSoftReference == null)
            {
                size -= (1 << 20);
                try
                {
                    lostSoftReference = new byte[size];
                }
                catch (OutOfMemoryError ignore)
                {}
            }
            clearedSoftReference = new SoftReference<byte[]>(lostSoftReference);

            lostSoftReference = null;
            ArrayList<byte[]> garbage = new ArrayList<byte[]>();
            while (clearedSoftReference.get() != null)
            {
                try
                {
                    garbage.add(new byte[size - (1 << 10)]);
                }
                catch (OutOfMemoryError ignore)
                {
                    // $JL-EXC$
                }
            }

            availableSoftReference = new SoftReference<String>(new String("availableSoftReference"));

            // Weak reference
            String lostWeakReference = new String("clearedWeakReference");

            clearedWeakReference = new WeakReference<String>(lostWeakReference);

            keptWeakReference = new String("keptWeakReference");
            availableWeakReference = new WeakReference<String>(keptWeakReference);
        }
    }
}
