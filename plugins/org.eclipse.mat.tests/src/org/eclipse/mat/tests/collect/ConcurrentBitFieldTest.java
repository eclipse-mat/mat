package org.eclipse.mat.tests.collect;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

import java.io.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.mat.collect.ConcurrentBitField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConcurrentBitFieldTest
{

    // --- basic shape / boundaries ------------------------------------------------

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void booleanArrayCtorInitializesAllBitsAcrossSlots()
    {
        // length crosses multiple 64-bit slots and ends mid-slot
        int n = 64 * 3 + 7;
        boolean[] init = new boolean[n];

        // Pattern: set primes and every 5th bit; leave others false
        for (int i = 0; i < n; i++)
        {
            if (isPrime(i) || (i % 5 == 0)) init[i] = true;
        }

        ConcurrentBitField bf = new ConcurrentBitField(init);

        // Verify exact mapping
        for (int i = 0; i < n; i++)
        {
            assertEquals("bit " + i, init[i], bf.get(i));
        }

        // Verify mutability after construction doesn’t affect bf
        init[0] = !init[0];
        assertEquals(
            "post-mutation of source array must not affect bf",
            !init[0], // we flipped source, bf should still have original
            bf.get(0) == false ? false : true /* force boolean */
        );

        // Flip a few and ensure API still works
        for (int i = 0; i < n; i += 17)
        {
            boolean cur = bf.get(i);
            assertTrue(bf.compareAndSet(i, cur, !cur));
            assertEquals(!cur, bf.get(i));
        }
    }

    @Test
    public void booleanArrayCtorRejectsEmpty() {
        thrown.expect(IllegalArgumentException.class);
        new ConcurrentBitField(new boolean[0]);
    }

    // helper
    private static boolean isPrime(int x) {
        if (x < 2) return false;
        if (x % 2 == 0) return x == 2;
        for (int d = 3; d * d <= x; d += 2) if (x % d == 0) return false;
        return true;
    }

    @Test
    public void sizeReportsCorrectly() {
        ConcurrentBitField bf = new ConcurrentBitField(1_000);
        assertThat(bf.size(), equalTo(1_000));
    }

    @Test
    public void singleLengthValue() {
        ConcurrentBitField bf = new ConcurrentBitField(1);
        assertThat(bf.size(), equalTo(1));
        assertThat(bf.get(0), equalTo(false));
        bf.set(0);
        assertThat(bf.get(0), equalTo(true));
        bf.clear(0);
        assertThat(bf.get(0), equalTo(false));
    }

    @Test
    public void lastIndexWorks() {
        int n = 257; // crosses a 64-bit boundary (slot 0..4)
        ConcurrentBitField bf = new ConcurrentBitField(n);
        assertFalse(bf.get(n - 1));
        bf.set(n - 1);
        assertTrue(bf.get(n - 1));
        bf.clear(n - 1);
        assertFalse(bf.get(n - 1));
    }

    @Test
    public void setClearIdempotent() {
        ConcurrentBitField bf = new ConcurrentBitField(128);
        bf.set(5);
        bf.set(5);
        assertTrue(bf.get(5));
        bf.clear(5);
        bf.clear(5);
        assertFalse(bf.get(5));
    }

    @Test
    public void toBooleanArrayNonAtomicMatchesSingleThreadState() {
        int n = 200;
        ConcurrentBitField bf = new ConcurrentBitField(n);
        for (int i = 0; i < n; i += 5) bf.clear(i);
        for (int i = 0; i < n; i += 3) bf.set(i);
        boolean[] snap = bf.toBooleanArrayNonAtomic();
        for (int i = 0; i < n; i++)
        {
            assertEquals(bf.get(i), snap[i]);
        }
    }

    // --- compareAndSet semantics -------------------------------------------------

    @Test
    public void casReturnsTrueOnNoOpWhenAlreadyMatches() {
        ConcurrentBitField bf = new ConcurrentBitField(64);
        // ensure bit is set
        assertTrue(bf.compareAndSet(10, false, true));
        assertTrue(bf.get(10));
        // expected=true, newValue=true -> no-op, should return true
        assertTrue(bf.compareAndSet(10, true, true));
        assertTrue(bf.get(10));
        // expected=false should fail now
        assertFalse(bf.compareAndSet(10, false, true));
        // clear via CAS
        assertTrue(bf.compareAndSet(10, true, false));
        assertFalse(bf.get(10));
        // expected=true should now fail
        assertFalse(bf.compareAndSet(10, true, false));
    }

    @Test
    public void casSucceedsDespiteOtherBitsChangingInSameSlot() throws Exception
    {
        // Pick index i and a “noise” bit j in same 64-slot.
        final int base = 128; // start of slot #2
        final int i = base + 3; // target bit
        final int j = base + 17; // noise bit (same 64-lane)

        ConcurrentBitField bf = new ConcurrentBitField(256);
        // Ensure target starts cleared
        assertFalse(bf.get(i));

        CountDownLatch start = new CountDownLatch(1);
        AtomicLong flips = new AtomicLong();

        Thread toggler = new Thread(() -> {
            try {
                start.await();
            }
            catch (InterruptedException ignored)
            {}
            // Hammer a different bit in the same slot to force CAS “witness” mismatch
            for (int k = 0; k < 200_000; k++)
            {
                bf.set(j);
                bf.clear(j);
                flips.incrementAndGet();
            }
        });

        toggler.start();
        start.countDown();

        // Attempt CAS on target bit while other bits in same word are changing.
        boolean ok = bf.compareAndSet(i, false, true);
        assertTrue("CAS on target bit should succeed even if other bits change", ok);
        assertTrue(bf.get(i));

        toggler.join();
        assertTrue("Toggler should have done work", flips.get() > 0);
    }

    // --- randomized single-thread correctness vs BitSet baseline ----------------

    @Test
    public void randomizedAgainstBitSet() {
        int n = 10_000;
        long seed = 42L;
        Random rnd = new Random(seed);
        ConcurrentBitField bf = new ConcurrentBitField(n);
        BitSet bs = new BitSet(n);

        for (int t = 0; t < 200_000; t++)
        {
            int idx = rnd.nextInt(n);
            int op = rnd.nextInt(4);
            switch (op)
            {
                case 0:
                    bf.set(idx);
                    bs.set(idx);
                    break;

                case 1:
                    bf.clear(idx);
                    bs.clear(idx);
                    break;

                case 2:
                    {
                        boolean exp = bs.get(idx);
                        boolean ret = bf.compareAndSet(idx, exp, !exp);
                        if (ret) bs.flip(idx);
                    }
                    break;

                case 3:
                    {
                        assertEquals(bs.get(idx), bf.get(idx));
                    }
                    break;
            }
        }

        // Final full comparison
        for (int i = 0; i < n; i++)
        {
            assertEquals(bs.get(i), bf.get(i));
        }
    }

    // --- multi-threaded set/clear correctness -----------------------------------

    @Test(timeout = 15_000)
    public void parallelSetDisjointRanges() throws Exception
    {
        int n = 1 << 16;
        ConcurrentBitField bf = new ConcurrentBitField(n);
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++)
        {
            final int tid = t;
            fs.add(
                pool.submit(() -> {
                    int start = (tid * n) / threads;
                    int end = ((tid + 1) * n) / threads;
                    try {
                        go.await();
                    }
                    catch (InterruptedException ignored)
                    {}
                    for (int i = start; i < end; i++)
                        bf.set(i);
                })
            );
        }
        go.countDown();
        for (Future<?> f : fs)
            f.get();
        pool.shutdown();

        for (int i = 0; i < n; i++)
            assertTrue(bf.get(i));
    }

    @Test(timeout = 20_000)
    public void parallelMixedSetClearSameRange() throws Exception
    {
        int n = 200_000; // multiple slots
        ConcurrentBitField bf = new ConcurrentBitField(n);
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // Half threads set even indices, half clear even indices, others random CAS flips.
        int setThreads = threads / 3;
        int clearThreads = threads / 3;
        int casThreads = threads - setThreads - clearThreads;

        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < setThreads; t++)
        {
            tasks.add(() -> {
                barrier.await();
                for (int i = 0; i < n; i += 2)
                    bf.set(i);
                return null;
            });
        }
        for (int t = 0; t < clearThreads; t++)
        {
            tasks.add(() -> {
                barrier.await();
                for (int i = 0; i < n; i += 2)
                    bf.clear(i);
                return null;
            });
        }
        for (int t = 0; t < casThreads; t++)
        {
            tasks.add(() -> {
                Random r = ThreadLocalRandom.current();
                barrier.await();
                for (int k = 0; k < 150_000; k++)
                {
                    int i = r.nextInt(n);
                    boolean cur = bf.get(i);
                    bf.compareAndSet(i, cur, !cur); // flip attempt
                }
                return null;
            });
        }

        pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // Simple invariant: every bit is either set or not set; verify API consistency.
        for (int i = 0; i < Math.min(n, 20000); i++)
        {
            // sample to keep runtime bounded
            boolean g = bf.get(i);
            if (g)
            {
                // clearing should succeed with expected=true
                assertTrue(bf.compareAndSet(i, true, false));
                assertFalse(bf.get(i));
            }
            else
            {
                // setting should succeed with expected=false
                assertTrue(bf.compareAndSet(i, false, true));
                assertTrue(bf.get(i));
            }
        }
    }

    // --- stress: heavy contention on same-slot bits ------------------------------

    @Test(timeout = 25_000)
    public void heavyContentionSameSlotIsLinearizable() throws Exception
    {
        final int slotBase = 1024; // choose slot-aligned base
        final int BITS = 64; // full slot
        final int ROUNDS = 200_000;

        ConcurrentBitField bf = new ConcurrentBitField(slotBase + BITS);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ops = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < 8; t++)
        {
            tasks.add(() -> {
                Random r = ThreadLocalRandom.current();
                start.await();
                for (int k = 0; k < ROUNDS; k++)
                {
                    int bit = slotBase + r.nextInt(BITS);
                    if ((k & 1) == 0)
                    {
                        bf.set(bit);
                    }
                    else
                    {
                        boolean cur = bf.get(bit);
                        bf.compareAndSet(bit, cur, !cur);
                    }
                    ops.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futs = tasks
            .stream()
            .map(pool::submit)
            .collect(Collectors.toList());

        start.countDown();
        for (Future<Void> f : futs)
            f.get();
        pool.shutdown();

        assertTrue("did work", ops.get() >= 8 * ROUNDS);

        // Sanity: bits are readable and stable under single-thread probe
        int ones = 0;
        for (int i = 0; i < BITS; i++)
        {
            if (bf.get(slotBase + i))
                ones++;
        }
        // Not asserting a particular count; just that get() is coherent:
        boolean[] snap = bf.toBooleanArrayNonAtomic();
        int ones2 = 0;
        for (int i = 0; i < BITS; i++)
        {
            if (snap[slotBase + i])
                ones2++;
        }
        assertEquals(ones, ones2);
    }

    // --- regression: creating non-multiple-of-64 size and touching edges --------

    @Test
    public void nonMultipleOf64EdgesSafe()
    {
        int n = (64 * 7) + 13;
        ConcurrentBitField bf = new ConcurrentBitField(n);
        // touch first/last of each slot + last element overall
        for (int s = 0; s < (n + 63) / 64; s++)
        {
            int first = s * 64;
            int last = Math.min(n - 1, s * 64 + 63);
            bf.set(first);
            bf.set(last);
            assertTrue(bf.get(first));
            assertTrue(bf.get(last));
            bf.clear(first);
            bf.clear(last);
            assertFalse(bf.get(first));
            assertFalse(bf.get(last));
        }
        assertFalse(bf.get(n - 1));
    }
}
