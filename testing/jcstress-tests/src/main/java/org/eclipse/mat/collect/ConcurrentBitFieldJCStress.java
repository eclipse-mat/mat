package org.eclipse.mat.collect;

import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.Z_Result;
import org.openjdk.jcstress.infra.results.ZZ_Result;
import org.openjdk.jcstress.infra.results.ZZZ_Result;

public class ConcurrentBitFieldJCStress {

    // 1) set vs set on same bit: final must be true.
    @JCStressTest
    @State
    @Outcome(id = "true",  expect = Expect.ACCEPTABLE, desc = "Bit set.")
    @Outcome(id = "false", expect = Expect.FORBIDDEN,  desc = "Idempotence violated.")
    public static class SetSetSameBit {
        final ConcurrentBitField bf = new ConcurrentBitField(128);
        final int i = 5;
        @Actor public void a1() { bf.set(i); }
        @Actor public void a2() { bf.set(i); }
        @Arbiter public void arb(Z_Result r) { r.r1 = bf.get(i); }
    }

    // 2) set vs clear on same bit: both end-states are valid.
    @JCStressTest
    @State
    @Outcome(id = "true",  expect = Expect.ACCEPTABLE, desc = "Set wins.")
    @Outcome(id = "false", expect = Expect.ACCEPTABLE, desc = "Clear wins.")
    public static class SetClearRaceSameBit {
        final ConcurrentBitField bf = new ConcurrentBitField(128);
        final int i = 7;
        @Actor public void a1() { bf.set(i); }
        @Actor public void a2() { bf.clear(i); }
        @Arbiter public void arb(Z_Result r) { r.r1 = bf.get(i); }
    }

    // 3) CAS must not be perturbed by other-bit churn in SAME slot.
    //    Only valid: cas=true, final=true.
    @JCStressTest
    @State
    @Outcome(id = "true, true",  expect = Expect.ACCEPTABLE, desc = "CAS succeeded; bit set.")
    @Outcome(id = "true, false", expect = Expect.FORBIDDEN,  desc = "Lost own update without a clearer on i.")
    @Outcome(id = "false, true", expect = Expect.FORBIDDEN,  desc = "CAS reported false though expected still held (wrong witness handling).")
    @Outcome(id = "false, false",expect = Expect.FORBIDDEN,  desc = "CAS should succeed from initial false.")
    public static class CasSurvivesOtherBitMutationSameSlot {
        final ConcurrentBitField bf = new ConcurrentBitField(128);
        final int i = 10;       // target bit for CAS (false -> true)
        final int j = 11;       // unrelated bit in same 64-bit slot
        @Actor public void a1(ZZ_Result r) { r.r1 = bf.compareAndSet(i, false, true); }
        @Actor public void a2() { bf.set(j); bf.clear(j); bf.set(j); }
        @Arbiter public void arb(ZZ_Result r) { r.r2 = bf.get(i); }
    }

    // 4) Conflicting CAS on SAME bit from initial false.
    // Valid triples:
    //   A(true,false,true)  : B runs first and fails; A sets true.
    //   A(true,true,false)  : A sets true; B flips it to false.
    // All others are forbidden.
    @JCStressTest
    @State
    @Outcome(id = "true, false, true",  expect = Expect.ACCEPTABLE, desc = "A wins; B fails; final true.")
    @Outcome(id = "true, true, false", expect = Expect.ACCEPTABLE, desc = "A then B; final false.")
    @Outcome(id = "false, true, false", expect = Expect.FORBIDDEN,  desc = "B cannot succeed before A from initial false.")
    @Outcome(id = "false, false, true", expect = Expect.FORBIDDEN,  desc = "A cannot fail from initial false.")
    @Outcome(id = "false, false, false",expect = Expect.FORBIDDEN,  desc = "Both failing from initial false is impossible.")
    @Outcome(id = "true, false, false", expect = Expect.FORBIDDEN,  desc = "A succeeded but final false without B success.")
    @Outcome(id = "false, true, true",  expect = Expect.FORBIDDEN,  desc = "B succeeded but final true.")
    @Outcome(id = "true, true, true",   expect = Expect.FORBIDDEN,  desc = "Both succeeded but final true (should be false).")
    public static class ConflictingCASSameBit {
        final ConcurrentBitField bf = new ConcurrentBitField(128);
        final int i = 20;
        @Actor public void a1(ZZZ_Result r) { r.r1 = bf.compareAndSet(i, false, true); }
        @Actor public void a2(ZZZ_Result r) { r.r2 = bf.compareAndSet(i, true, false); }
        @Arbiter public void arb(ZZZ_Result r) { r.r3 = bf.get(i); }
    }

    // 5) CAS independent across different slots.
    // Only valid: cas=true, final=true.
    @JCStressTest
    @State
    @Outcome(id = "true, true",  expect = Expect.ACCEPTABLE, desc = "Independent slot churn ignored.")
    @Outcome(id = "true, false", expect = Expect.FORBIDDEN,  desc = "Own update lost without clearer on i.")
    @Outcome(id = "false, true", expect = Expect.FORBIDDEN,  desc = "CAS should not fail from initial false.")
    @Outcome(id = "false, false",expect = Expect.FORBIDDEN,  desc = "CAS should not fail from initial false.")
    public static class CasIndependentAcrossSlots {
        final ConcurrentBitField bf = new ConcurrentBitField(256);
        final int i = 3;         // slot 0
        final int k = 3 + 64;    // slot 1
        @Actor public void a1(ZZ_Result r) { r.r1 = bf.compareAndSet(i, false, true); }
        @Actor public void a2() { bf.set(k); bf.clear(k); bf.set(k); bf.clear(k); }
        @Arbiter public void arb(ZZ_Result r) { r.r2 = bf.get(i); }
    }

    // 6) clear vs clear on same bit: final must be false.
    @JCStressTest
    @State
    @Outcome(id = "false", expect = Expect.ACCEPTABLE, desc = "Bit cleared.")
    @Outcome(id = "true",  expect = Expect.FORBIDDEN,  desc = "Clear is not idempotent if ends true.")
    public static class ClearClearSameBit {
        final ConcurrentBitField bf = new ConcurrentBitField(128);
        final int i = 9;
        { bf.set(i); } // start true, two clears race
        @Actor public void a1() { bf.clear(i); }
        @Actor public void a2() { bf.clear(i); }
        @Arbiter public void arb(Z_Result r) { r.r1 = bf.get(i); }
    }
}
