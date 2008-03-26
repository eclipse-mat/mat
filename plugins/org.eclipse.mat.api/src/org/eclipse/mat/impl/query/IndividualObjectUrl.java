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
package org.eclipse.mat.impl.query;

import java.math.BigInteger;

public class IndividualObjectUrl extends DiggerUrl
{
    final static String TYPE = "obj"; //$NON-NLS-1$
    private final static String INDIVIDUALOBJECT = "o: "; //$NON-NLS-1$
    private final static String NUMBER_PREFIX = "0x"; //$NON-NLS-1$

    private Long lObjectAddress;
    private String sObjectAddress;

    public IndividualObjectUrl(String objSpec)
    {
        if (objSpec.startsWith(NUMBER_PREFIX))
            content = INDIVIDUALOBJECT + objSpec;
        else
            content = objSpec;
        
        sObjectAddress = content.substring(INDIVIDUALOBJECT.length());
        
        type = TYPE;
        label = sObjectAddress;
    }

    public IndividualObjectUrl(long objectAddress)
    {
        this(NUMBER_PREFIX + Long.toString(objectAddress, 16));
        lObjectAddress = objectAddress;
    }
    
    public IndividualObjectUrl(long objectAddress, String label)
    {
        this(objectAddress);
        this.label = label;
    }

    public long getObjectAddress()
    {
        if (lObjectAddress == null)
            lObjectAddress = new BigInteger(sObjectAddress.substring(NUMBER_PREFIX.length()), 16).longValue();

        return lObjectAddress;
    }

}
