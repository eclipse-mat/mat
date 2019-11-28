/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - unions
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import java.util.regex.Pattern;

import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.util.MessageUtil;

/**
 * Factory for often-used OQL queries.
 */
public final class OQL
{

    /**
     * Select object by its address.
     */
    public static final String forAddress(long address)
    {
        return "SELECT * FROM OBJECTS 0x" + Long.toHexString(address);//$NON-NLS-1$
    }

    /**
     * Select object by its object id.
     */
    public static final String forObjectId(int objectId)
    {
        return "SELECT * FROM OBJECTS " + objectId;//$NON-NLS-1$
    }

    /**
     * Select objects by its ids.
     */
    public static String forObjectIds(int[] objectIds)
    {
        if (objectIds.length == 0)
            return null;
        StringBuilder buf = new StringBuilder(512);
        buf.append("SELECT * FROM OBJECTS "); //$NON-NLS-1$

        for (int ii = 0; ii < objectIds.length; ii++)
        {
            if (ii > 0)
                buf.append(","); //$NON-NLS-1$
            buf.append(objectIds[ii]);
        }

        return buf.toString();
    }

    /**
     * Select the retained set of a given OQL query.
     */
    public static final String retainedBy(String oqlQuery)
    {
        return "SELECT AS RETAINED SET * FROM OBJECTS (" + oqlQuery + ")";//$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Select the retained set of a given object.
     */
    public static String retainedBy(int objectId)
    {
        return "SELECT AS RETAINED SET * FROM OBJECTS " + objectId;//$NON-NLS-1$
    }

    /**
     * All objects of a given class.
     */
    public static final String forObjectsOfClass(IClass clasz)
    {
        return "SELECT * FROM " + clasz.getName();//$NON-NLS-1$
    }

    /**
     * All objects of a class identified by its id.
     */
    public static final String forObjectsOfClass(int classId)
    {
        return "SELECT * FROM " + classId; //$NON-NLS-1$
    }

    /**
     * Find the last identifier of the query
     * @param query
     * @return the identifier, including a space
     */
    private static CharSequence lastId(CharSequence query, int end)
    {
        int j = end - 1;
        while (j >= 0 && Character.isJavaIdentifierPart(query.charAt(j))) --j;
        if (j < end - 1 && Character.isJavaIdentifierStart(query.charAt(j + 1)))
        {
            if (isSpace(query, j))
                return query.subSequence(j, end);
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * Extract out digits, spaces and commas going backward
     * @param s
     * @param e start position
     * @return the sequence of digits, spaces, commas
     */
    private static CharSequence matchObjs(CharSequence s, int e) {
        int i = e - 1;
        while (isSpace(s, i))
            --i;
        if (!isDigit(s, i))
            return ""; //$NON-NLS-1$
        for (;;)
        {
            if (!isDigit(s, i))
                return s.subSequence(i+1, e);
            while (isDigit(s, i))
                --i;
            while (isSpace(s, i))
                --i;
            if (i < 0 || s.charAt(i) != ',')
                return s.subSequence(i+1, e);
            --i;
            while (isSpace(s, i))
                --i;
        }
    }

    private static boolean isDigit(CharSequence s, int i)
    {
        char c;
        return i >= 0 && i < s.length() && (c = s.charAt(i)) >= '0' && c <= '9';
    }

    private static boolean isSpace(CharSequence s, int i)
    {
        char c;
        return i >= 0 && i < s.length() &&
                        ((c = s.charAt(i)) == ' ' ||
                        c == '\t' || c == '\n' || c == '\r' || c == '\f');
    }

    /**
     * Create a OQL union statement and append it to the query.
     * Possibly optimize a common prefix.
     * select s.a,s.b,s.c from 1,173 s
     * select s.a,s.b,s.c from 123 s
     * combine to
     * select s.a,s.b,s.c from 1,173,123 s
     *
     * Also split off UNION clauses to see if the new clause can
     * be merged into an existing UNION clause.
     */
    public static void union(StringBuilder query, String other)
    {
        if ((query.length() > 0))
        {
            int end = query.length();
            while (query.charAt(end - 1) == ')')
            {
                int start = query.lastIndexOf(" UNION (", end - 1); //$NON-NLS-1$
                if (start == -1)
                    break;
                if (union(query, start + 8, end - 1, other))
                    return;
                if (start < 1)
                    break;
                end = start;
            }
            if (union(query, 0, end, other))
                return;
            // Default
            query.append(" UNION (").append(other).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else
            query.append(other);
    }

    private static boolean union(StringBuilder query, int start, int end, String other)
    {
        // Strip off last identifier
        CharSequence id1 = lastId(query, end);
        CharSequence id2 = lastId(other, other.length());
        if (id1.equals(id2))
        {
            // Find the object identifiers
            CharSequence num1 = matchObjs(query, end - id1.length());
            CharSequence num2 = matchObjs(other, other.length() - id2.length());
            int s1 = end - id1.length() - num1.length();
            int s2 = other.length() - id2.length() - num2.length();
            if (num1.length() > 0 && num2.length() > 0 &&
                            query.subSequence(start, s1).equals(
                                            other.subSequence(0, s2)))
            {
                int j = 0;
                while (isSpace(num2, j)) ++j;
                query.insert(s1 + num1.length(), ","+num2.subSequence(j,  num2.length())); //$NON-NLS-1$
                return true;
            }
        }
        return false;
    }

    /**
     * Return all instances of classes matching a given regular expression.
     */
    public static String instancesByPattern(Pattern pattern, boolean includeSubclasses)
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append("SELECT * FROM \""); //$NON-NLS-1$
        if (includeSubclasses)
            buf.append(" INSTANCEOF"); //$NON-NLS-1$
        buf.append(pattern.pattern());
        buf.append("\""); //$NON-NLS-1$

        return buf.toString();
    }

    /**
     * Returns all classes matching a given regular expression.
     */
    public static String classesByPattern(Pattern pattern, boolean includeSubclasses)
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append("SELECT * FROM OBJECTS \""); //$NON-NLS-1$
        if (includeSubclasses)
            buf.append(" INSTANCEOF"); //$NON-NLS-1$
        buf.append(pattern.pattern());
        buf.append("\""); //$NON-NLS-1$

        return buf.toString();
    }

    private static final String OQL_classesByClassLoaderId = "SELECT * FROM java.lang.Class c WHERE c implements " //$NON-NLS-1$
                    + IClass.class.getName() + " and c.@classLoaderId = {0,number,0}"; //$NON-NLS-1$

    /**
     * Returns an OQL query string to select all objects loaded by the given
     * class loader.
     *
     * <pre>
     *       select *
     *       from
     *       (
     *            select *
     *            from java.lang.Class c
     *            where
     *                c implements org.eclipse.mat.snapshot.model.IClass
     *                and c.@classLoaderId = {0}
     *       )
     * </pre>
     *
     * @param classLoaderId
     *            the object id of the class loader
     * @return an OQL query selecting all objects loaded by the class loader
     */
    public static String instancesByClassLoaderId(int classLoaderId)
    {
        StringBuilder buf = new StringBuilder(256);
        buf.append("SELECT * FROM ("); //$NON-NLS-1$
        buf.append(classesByClassLoaderId(classLoaderId));
        buf.append(")"); //$NON-NLS-1$
        return buf.toString();
    }

    /**
     * Returns an OQL query string to select all classes loaded by the given
     * class loader.
     *
     * <pre>
     *       select *
     *       from java.lang.Class c
     *       where
     *            c implements org.eclipse.mat.snapshot.model.IClass
     *            and c.@classLoaderId = {0}
     * </pre>
     *
     * @param classLoaderId
     *            the object id of the class loader
     * @return an OQL query selecting all classes loaded by the class loader
     */
    public static String classesByClassLoaderId(int classLoaderId)
    {
        return MessageUtil.format(OQL_classesByClassLoaderId, new Object[] { classLoaderId });
    }

    private OQL()
    {}
}
