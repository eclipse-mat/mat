package org.eclipse.mat.snapshot;

import java.util.ArrayList;
import java.util.List;

public class SnapshotFormat
{
    private String name;
    private String[] fileExtensions;

    public SnapshotFormat(String name, String[] fileExtensions)
    {
        this.fileExtensions = fileExtensions;
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public String[] getFileExtensions()
    {
        return fileExtensions;
    }

    // //////////////////////////////////////////////////////////////
    // static
    // //////////////////////////////////////////////////////////////

    private static final List<SnapshotFormat> ELEMENTS = new ArrayList<SnapshotFormat>();

    public static synchronized void add(SnapshotFormat format)
    {
        ELEMENTS.add(format);
    }

    public static synchronized void remove(SnapshotFormat format)
    {
        ELEMENTS.remove(format);
    }

    public static synchronized List<SnapshotFormat> all()
    {
        return new ArrayList<SnapshotFormat>(ELEMENTS);
    }

}
