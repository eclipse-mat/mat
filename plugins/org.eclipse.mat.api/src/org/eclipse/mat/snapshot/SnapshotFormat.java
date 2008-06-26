package org.eclipse.mat.snapshot;

/**
 * @noinstantiate
 */
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
}
