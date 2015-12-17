/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.IteratorInt;
import org.eclipse.mat.snapshot.model.IObject;

public abstract class SimpleObjectQuery<O extends IOverviewNode> extends BaseObjectQuery<O> {
    public SimpleObjectQuery(Class<O> oClass) {
        super(oClass);
    }

    protected List<O> buildResults(ArrayInt source) throws SnapshotException {
        List<O> result = new ArrayList<O>();
        IteratorInt it = source.iterator();
        while (it.hasNext()) {
            int id = it.next();
            O o = createOverviewNode(snapshot.getObject(id));
            if (o != null)
                result.add(o);
        }
        return result;
    }

    protected abstract O createOverviewNode(IObject obj);
}
