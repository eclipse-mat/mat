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
package org.eclipse.mat.impl.snapshot.notes;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Observable;

import org.eclipse.mat.snapshot.ISnapshot;


public class NotesManager extends Observable
{
    private static final NotesManager INSTANCE = new NotesManager();

    public static NotesManager instance()
    {
        return INSTANCE;
    }

    private NotesManager()
    {}

    public StringBuffer getSnapshotNotes(String snapshotPath)
    {
        try
        {
            if (snapshotPath != null)
            {
                File notesFile = new File(getDefaultNotesFile(snapshotPath));
                if (notesFile.exists())
                {
                    FileReader fileReader = new FileReader(getDefaultNotesFile(snapshotPath));

                    BufferedReader myInput = new BufferedReader(fileReader);

                    try
                    {
                        String s;
                        StringBuffer b = new StringBuffer();
                        while ((s = myInput.readLine()) != null)
                        {
                            b.append(s);
                            b.append("\n");
                        }
                        return b;
                    }
                    finally
                    {
                        try
                        {
                            myInput.close();
                        }
                        catch (IOException ignore)
                        {
                            // $JL-EXC$
                        }
                    }
                }
            }
            return null;
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public StringBuffer getSnapshotNotes(ISnapshot snapshot)
    {
        return getSnapshotNotes(snapshot.getSnapshotInfo().getPath());
    }

    private static String getDefaultNotesFile(String snapshotPath)
    {
        int p = snapshotPath.lastIndexOf('.');
        return snapshotPath.substring(0, p + 1) + "notes.txt";
    }

    public void saveNotes(String snapshotPath, String notes)
    {
        try
        {
            File notesFile = new File(getDefaultNotesFile(snapshotPath));
            // if (notesFile.exists())
            // {
            OutputStream fout = new FileOutputStream(notesFile);
            OutputStream bout = new BufferedOutputStream(fout);
            OutputStreamWriter out = new OutputStreamWriter(bout, "UTF8");
            out.write(notes);
            out.flush();
            out.close();
            // }

        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
