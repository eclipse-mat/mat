/*******************************************************************************
 * Copyright (c) 2016 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ruby.nameresolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;

@Subject("String")
public class StringResolver implements IClassSpecificNameResolver
{

    static Map<String, Map<Long, String>> dump2Strings = new HashMap<>();

    public StringResolver()
    {
    }

    @Override
    public String resolve(IObject object) throws SnapshotException
    {
        // FIXME: Figure out how to read the objects, without needing to extract the strings only
        String filename = object.getSnapshot().getSnapshotInfo().getPath() + ".strings";
        Map<Long, String> strings = dump2Strings.get(filename);
        if (strings == null)
        {
            File f = new File(filename);
            if (f.exists())
            {
                strings = readStringValues(f);
                dump2Strings.put(filename, strings);
            }
        }

        if (strings != null) { return strings.get(object.getObjectAddress()); }

        return null;
    }

    private Map<Long, String> readStringValues(File f) throws SnapshotException
    {
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(f));

            Map<Long, String> result = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null)
            {
                int spaceIndex = line.indexOf(' ');
                if (spaceIndex > 0)
                {
                    long key = Long.parseLong(line.substring(2, spaceIndex), 16);
                    String value = line.substring(spaceIndex + 1);
                    result.put(key, URLDecoder.decode(value, "UTF8"));
                }

            }
            return result;
        }
        catch (IOException e)
        {
            throw new SnapshotException(e);
        }
        finally
        {
            if (reader != null)
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
        }
    }

}
