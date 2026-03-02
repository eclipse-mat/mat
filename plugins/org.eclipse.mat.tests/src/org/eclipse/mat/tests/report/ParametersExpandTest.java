/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.report.internal.Parameters;
import org.junit.Test;

public class ParametersExpandTest
{
    private static Parameters params(Map<String, String> map)
    {
        return new Parameters.Deep(map);
    }

    @Test
    public void testNoSubstitution()
    {
        assertEquals("hello world", params(Collections.emptyMap()).expand("hello world")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testSimpleSubstitution()
    {
        Map<String, String> map = new HashMap<String, String>();
        map.put("name", "world"); //$NON-NLS-1$//$NON-NLS-2$
        assertEquals("hello world", params(map).expand("hello ${name}")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testUnknownKeyBecomesEmpty()
    {
        assertEquals("hello ", params(Collections.emptyMap()).expand("hello ${missing}")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testEscapeProducesLiteralBraces()
    {
        assertEquals("hello ${name}", params(Collections.emptyMap()).expand("hello $${name}")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testEscapeIsNotSubstituted()
    {
        Map<String, String> map = new HashMap<String, String>();
        map.put("name", "world"); //$NON-NLS-1$//$NON-NLS-2$
        assertEquals("hello ${name}", params(map).expand("hello $${name}")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testEscapeAndSubstitutionInSameString()
    {
        Map<String, String> map = new HashMap<String, String>();
        map.put("p", "X"); //$NON-NLS-1$//$NON-NLS-2$
        assertEquals("${p} X", params(map).expand("$${p} ${p}")); //$NON-NLS-1$//$NON-NLS-2$
    }

    @Test
    public void testOQLVariableEscapePattern()
    {
        // Mirrors the real use case: an OQL subquery reference like ${adder}
        // must survive Parameters.expand() when written as $${adder}
        Map<String, String> map = new HashMap<String, String>();
        String oql = "oql \"SELECT * FROM OBJECTS $${adder}.cells\""; //$NON-NLS-1$
        String expected = "oql \"SELECT * FROM OBJECTS ${adder}.cells\""; //$NON-NLS-1$
        assertEquals(expected, params(map).expand(oql));
    }

    @Test
    public void testNullReturnsNull()
    {
        assertNull(params(Collections.emptyMap()).expand(null));
    }

    @Test
    public void testUnclosedBraceIsLeftAlone()
    {
        assertEquals("${unclosed", params(Collections.emptyMap()).expand("${unclosed")); //$NON-NLS-1$//$NON-NLS-2$
    }
}
