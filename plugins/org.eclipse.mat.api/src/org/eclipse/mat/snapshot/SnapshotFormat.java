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
package org.eclipse.mat.snapshot;

/**
 * Summary of a parser for the snapshot
 * @noinstantiate
 */
public class SnapshotFormat
{
    private String name;
    private String[] fileExtensions;

    /**
     * Create summary information about a parser
     * @param name name of the parser type
     * @param fileExtensions file extensions it handles
     */
    public SnapshotFormat(String name, String[] fileExtensions)
    {
        this.fileExtensions = fileExtensions;
        this.name = name;
    }

    /**
     * Get the parser name
     * @return the parser name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Get the file extensions.
     * Used for filtering files in a file dialog when choosing a snapshot to open
     * @return an array of file extensions
     */
    public String[] getFileExtensions()
    {
        return fileExtensions;
    }
}
