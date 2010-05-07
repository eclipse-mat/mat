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

/**
 * Ease use of regular expressions on heap objects.
 * <ul>
 * <li>if the pattern does not contain one of the expressions .* !^ (at the
 * beginning) $ (at the end), then a .* is added at the beginning and at the end
 * of the pattern
 * <li>if the pattern contains [], it is replaced by \[\]
 * <li>if the pattern contains $ not at the end (inner classes), it is replaced
 * by \$
 * </ul>
 */
public class PatternUtil
{
    /**
     * Fix up a pattern to be a true regular expression pattern.
     * Add dots and starts at the beginning and end if not already there. 
     * @param pattern
     * @return the fixed-up pattern
     */
    public static String smartFix(String pattern)
    {
        return smartFix(pattern, true);
    }

    /**
     * Fix up a pattern to be a true regular expression pattern.
     * @param pattern
     * @param addDotStars if true then if the pattern does not contain one of the expressions .* !^
        (at the beginning) $ (at the end), then a .* is added at the
        beginning and at the end of the pattern.
     * @return the fixed-up pattern
     */
    @SuppressWarnings("nls")
    public static String smartFix(String pattern, boolean addDotStars)
    {
        if (pattern == null || pattern.trim().length() == 0)
        {
            return "";
        }
        else
        {
            int len = pattern.length();
            StringBuilder result = new StringBuilder(len << 1);
            char l, c = ' ';

            for (int ii = 0; ii < len; ii++)
            {
                l = c;
                c = pattern.charAt(ii);

                // if the pattern contains $ not at the end (inner classes), it
                // is replaced
                if ((l != '\\') && (c == '$') && (ii + 1 != len))
                {
                    result.append("\\$");
                }
                // if the pattern contains [], it is replaced by \[\]
                else if ((l == '[') && (c == ']'))
                {
                    result.insert(result.length() - 1, '\\');
                    result.append("\\]");
                }
                else
                {
                    result.append(c);
                }
            }

            String answer = result.toString();

            if (addDotStars)
            {
                // if the pattern does not contain one of the expressions .* !^
                // (at the beginning) $ (at the end), then a .* is added at the
                // beginning and at the end of the pattern
                if (!(answer.indexOf(".*") >= 0 || answer.charAt(0) == '^' || answer.charAt(answer.length() - 1) == '$'))
                {
                    answer = ".*" + answer + ".*";
                }
            }

            return answer;
        }
    }
}
