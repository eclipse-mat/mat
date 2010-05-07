/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A simple way of splitting up a String.
 */
public final class SimpleStringTokenizer implements Iterable<String>
{
    private String subject;
    private char delim;

    /**
     * Gets the different part of a string which are separated by the delimiter.
     * @param subject
     * @param delim
     */
    public SimpleStringTokenizer(String subject, char delim)
    {
        this.subject = subject;
        this.delim = delim;
    }

    public Iterator<String> iterator()
    {
        return new Iterator<String>()
        {
            int position = 0;
            int maxPosition = subject.length();

            public boolean hasNext()
            {
                return position < maxPosition;
            }

            public String next()
            {
                if (position >= maxPosition)
                    throw new NoSuchElementException();

                String answer;

                int p = subject.indexOf(delim, position);

                if (p < 0)
                {
                    answer = subject.substring(position);
                    position = maxPosition;
                    return answer;
                }
                else
                {
                    answer = subject.substring(position, p);
                    position = p + 1;
                }

                return answer;
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Splits the string at the delimiter character.
     * @param subject
     * @param delim
     * @return the string split at the delimiter
     */
    public static String[] split(String subject, char delim)
    {
        List<String> answer = new ArrayList<String>();
        for (String s : new SimpleStringTokenizer(subject, delim))
            answer.add(s.trim());
        return answer.toArray(new String[0]);
    }
}
