/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.collect;

import static org.junit.Assert.assertEquals;

import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.query.registry.Converters;
import org.junit.Test;

public class CommandTests
{
    @Test
    public void testToken1()
    {
        String s[] = CommandLine.tokenize("abc");
        assertEquals(1, s.length);
        assertEquals("abc", s[0]);
    }
    
    @Test
    public void testToken2()
    {
        String s[] = CommandLine.tokenize("abc def");
        assertEquals(2, s.length);
        assertEquals("abc", s[0]);
        assertEquals("def", s[1]);
    }
    
    @Test
    public void testToken3()
    {
        String a = "C:\\abc\\def ghi\\jkl.txt";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
    
    @Test
    public void testToken4()
    {
        String a = "C:\\abc\\defghi\"jkl.txt";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
    
    @Test
    public void testToken5()
    {
        String a = "\\\\MACHINE\\RESOURCE\\abc\\defghi\"jkl.txt";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
    
    @Test
    public void testToken6()
    {
        String a = "\\\\MACHINE\\RESOURCE\\abc\\defghi\"jkl.txt\"";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
    
    @Test
    public void testToken7()
    {
        String a = "\\\\MACHINE\\RESOURCE\\abc\\defghi\"jkl.txt\\\"";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
    
    @Test
    public void testToken8()
    {
        String a = "\\\\MACHINE\\RESOURCE\\abc\\defghi\"jkl.txt\\\\\"";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }

    @Test
    public void testToken9()
    {
        String a = "\\\\MACHINE\\RESOURCE\\a\tbc\\defghi\"jkl.txt\\\\\"";
        String b = Converters.convertAndEscape(a.getClass(), a);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }

    @Test
    public void testToken10()
    {
        String a = "//MACHINE/RESOURCE/a.txt";
        String b = Converters.convertAndEscape(a.getClass(), a);
        assertEquals(a, b);
        String s[] = CommandLine.tokenize(b);
        assertEquals(1, s.length);
        assertEquals(a, s[0]);
    }
}
