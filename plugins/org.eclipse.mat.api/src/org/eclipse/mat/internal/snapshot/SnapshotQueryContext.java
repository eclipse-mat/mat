/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot;

import java.io.File;
import java.math.BigInteger;
import java.text.ParsePosition;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.registry.QueryContextImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;

public class SnapshotQueryContext extends QueryContextImpl
{
    private ISnapshot snapshot;

    public SnapshotQueryContext(ISnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    public File getPrimaryFile()
    {
        return new File(snapshot.getSnapshotInfo().getPath());
    }

    public String mapToExternalIdentifier(int objectId) throws SnapshotException
    {
        long address = snapshot.mapIdToAddress(objectId);
        return "0x" + Long.toHexString(address); //$NON-NLS-1$
    }

    public int mapToObjectId(String externalIdentifier) throws SnapshotException
    {
        long objectAddress = new BigInteger(externalIdentifier.substring(2), 16).longValue();
        return snapshot.mapAddressToId(objectAddress);
    }

    // //////////////////////////////////////////////////////////////
    // context available
    // //////////////////////////////////////////////////////////////

    @Override
    public boolean available(Class<?> type, Advice advice)
    {
        try
        {
            if (type.isAssignableFrom(ISnapshot.class) && advice == null)
            {
                return true;
            }
            else if (type.isAssignableFrom(SnapshotInfo.class))
            {
                return true;
            }
            else if (snapshot.getSnapshotAddons(type) != null)
            {
                return true;
            }
            else
            {
                return super.available(type, advice);
            }
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object get(Class<?> type, Advice advice)
    {
        if (type.isAssignableFrom(ISnapshot.class) && advice == null)
        {
            return snapshot;
        }
        else if (type.isAssignableFrom(SnapshotInfo.class)) //
        { //
            return snapshot.getSnapshotInfo();
        }

        try
        {
            Object s = snapshot.getSnapshotAddons(type);
            if (s != null)
                return s;
        }
        catch (SnapshotException e)
        {
            throw new RuntimeException(e);
        }

        return super.get(type, advice);
    }

    // //////////////////////////////////////////////////////////////
    // available via conversion
    // //////////////////////////////////////////////////////////////

    @Override
    public boolean converts(Class<?> type, Advice advice)
    {
        if (type.isAssignableFrom(ISnapshot.class) && advice == Advice.SECONDARY_SNAPSHOT)
        {
            return true;
        }
        else if (type.isAssignableFrom(int.class) && advice == Advice.HEAP_OBJECT)
        {
            return true;
        }
        else if (type.isAssignableFrom(IObject.class))
        {
            return true;
        }
        else if (type.isAssignableFrom(IHeapObjectArgument.class)) //
        { //
            return true;
        }

        return super.converts(type, advice);
    }

    @Override
    public String convertToString(Class<?> type, Advice advice, Object value) throws SnapshotException
    {
        if (type.isAssignableFrom(ISnapshot.class) && advice == Advice.SECONDARY_SNAPSHOT)
        {
            return ((SnapshotArgument) value).getFilename();
        }
        else if (type.isAssignableFrom(int.class) && advice == Advice.HEAP_OBJECT)
        {
            return mapToExternalIdentifier((Integer) value);
        }
        else if (type.isAssignableFrom(IObject.class))
        {
            return mapToExternalIdentifier(((IObject) value).getObjectId());
        }
        else if (type.isAssignableFrom(IHeapObjectArgument.class)) //
        { //
            // it's not a two-way conversion. I can convert from string into
            // object, but not always the other way around...
            return value.toString();
        }

        return super.convertToString(type, advice, value);
    }

    @Override
    public Object convertToValue(Class<?> type, Advice advice, String value) throws SnapshotException
    {
        if (type.isAssignableFrom(ISnapshot.class) && advice == Advice.SECONDARY_SNAPSHOT)
        {
            return new SnapshotArgument(value);
        }
        else if (type.isAssignableFrom(int.class) && advice == Advice.HEAP_OBJECT)
        {
            return ArgumentParser.consumeHeapObjects(snapshot, value);
        }
        else if (type.isAssignableFrom(IObject.class))
        {
            return ArgumentParser.consumeHeapObjects(snapshot, value);
        }
        else if (type.isAssignableFrom(IHeapObjectArgument.class)) //
        { //
            return ArgumentParser.consumeHeapObjects(snapshot, value);
        }

        return super.convertToValue(type, advice, value);
    }

    // //////////////////////////////////////////////////////////////
    // special parsing required
    // //////////////////////////////////////////////////////////////

    public boolean parses(Class<?> type, Advice advice)
    {
        if (type.isAssignableFrom(int.class) && advice == Advice.HEAP_OBJECT)
        {
            return true;
        }
        else if (type.isAssignableFrom(IObject.class))
        {
            return true;
        }
        else if (type.isAssignableFrom(IHeapObjectArgument.class)) //
        { //
            return true;
        }

        return false;
    }

    public Object parse(Class<?> type, Advice advice, String[] args, ParsePosition pos) throws SnapshotException
    {
        return ArgumentParser.consumeHeapObjects(snapshot, args, pos);
    }

    // //////////////////////////////////////////////////////////////
    // synthetic columns
    // //////////////////////////////////////////////////////////////

    public ContextDerivedData getContextDerivedData()
    {
        return new RetainedSizeDerivedData(snapshot);
    }

}
