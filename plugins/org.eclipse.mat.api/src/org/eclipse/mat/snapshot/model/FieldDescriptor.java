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
package org.eclipse.mat.snapshot.model;

import java.io.Serializable;

/**
 * Describes a field of an object, i.e. its name and signature.
 */
public class FieldDescriptor implements Serializable
{
    private static final long serialVersionUID = 1L;

    protected String name;
    protected String signature;

    public FieldDescriptor(String name, String signature)
    {
        this.name = name;
        this.signature = signature;
    }

    public String getName()
    {
        return name;
    }

    public String getSignature()
    {
        return signature;
    }

    public void setName(String string)
    {
        name = string;
    }

    public void setSignature(String string)
    {
        signature = string;
    }

    public String getVerboseSignature()
    {
        if ("I".equals(signature))
            return "int";
        else if ("Z".equals(signature))
            return "boolean";
        else if ("C".equals(signature))
            return "char";
        else if ("F".equals(signature))
            return "float";
        else if ("D".equals(signature))
            return "double";
        else if ("B".equals(signature))
            return "byte";
        else if ("S".equals(signature))
            return "short";
        else if ("J".equals(signature))
            return "long";
        else if ("L".equals(signature))
            return "ref";
        else
            return signature;
    }
}
