package org.eclipse.mat.tests.snapshot;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

public class TestUnreachableObjects
{

    @Test
    public void testSunJDK5_64() throws SnapshotException
    {
        compare(TestSnapshots.SUN_JDK5_64BIT);
    }

    @Test
    public void testSunJDK6_32() throws SnapshotException
    {
        compare(TestSnapshots.SUN_JDK6_32BIT);
    }

    @Test
    public void testIBMJDK6_32_SYSTEM() throws SnapshotException
    {
        compare(TestSnapshots.IBM_JDK6_32BIT_SYSTEM);
    }

    // IBM PHD files do not have accurate roots, so this test won't work for IBM_JDK6_32BIT_HEAP

    private void compare(String snapshotName) throws SnapshotException
    {
        Map<String, String> options = new HashMap<String, String>();
        options.put("keep_unreachable_objects", "true");

        ISnapshot unreachables = TestSnapshots.getSnapshot(snapshotName, options, true);
        ISnapshot classic = TestSnapshots.getSnapshot(snapshotName, true);

        Histogram fullHistogram = unreachables.getHistogram(new VoidProgressListener());

        Histogram reachableHistogram = classic.getHistogram(new VoidProgressListener());
        UnreachableObjectsHistogram unreachableHistogram = classic.getSnapshotAddons(UnreachableObjectsHistogram.class);

        Map<String, Record> test = new HashMap<String, Record>();
        for (ClassHistogramRecord classHistogramRecord : fullHistogram.getClassHistogramRecords())
        {
            Record record = new Record(classHistogramRecord.getLabel(), classHistogramRecord.getNumberOfObjects(),
                            classHistogramRecord.getUsedHeapSize());
            test.put(classHistogramRecord.getLabel(), record);
        }

        for (ClassHistogramRecord classHistogramRecord : reachableHistogram.getClassHistogramRecords())
        {
            String key = classHistogramRecord.getLabel();
            assert test.containsKey(key) : "Class not found: " + key;
            Record record = test.get(key);
            record.objectCount = record.objectCount - classHistogramRecord.getNumberOfObjects();
            record.shallowHeapSize = record.shallowHeapSize - classHistogramRecord.getUsedHeapSize();
        }

        for (UnreachableObjectsHistogram.Record unreachableRecord : unreachableHistogram.getRecords())
        {
            String key = unreachableRecord.getClassName();
            assert test.containsKey(key):  "Class not found: " + key;
            Record record = test.get(key);
            record.objectCount = record.objectCount - unreachableRecord.getObjectCount();
            record.shallowHeapSize = record.shallowHeapSize - unreachableRecord.getShallowHeapSize();
        }

        for (Map.Entry<String, Record> entry : test.entrySet())
        {
            Record record = entry.getValue();
            assert record.getObjectCount() == 0 : "illegal count = " + record;
            assert record.getShallowHeapSize() == 0 : "illegal size = " + record;
        }
    }

    private class Record
    {
        private String className;
        private long objectCount;
        private long shallowHeapSize;

        public Record(String className, long nrOfObjects, long sizeOfObjects)
        {
            this.className = className;
            this.objectCount = nrOfObjects;
            this.shallowHeapSize = sizeOfObjects;
        }

        public String getClassName()
        {
            return className;
        }

        public long getObjectCount()
        {
            return objectCount;
        }

        public long getShallowHeapSize()
        {
            return shallowHeapSize;
        }

        @Override
        public String toString()
        {
            return className + " count=" + objectCount + " size=" + shallowHeapSize;
        }

    }
}
