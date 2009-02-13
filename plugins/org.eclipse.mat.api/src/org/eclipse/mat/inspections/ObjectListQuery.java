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
package org.eclipse.mat.inspections;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.annotations.Menu.Entry;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.ObjectListResult;
import org.eclipse.mat.util.IProgressListener;

@Name("List Objects")
@Category(Category.HIDDEN)
@CommandName("list_objects")
@Menu( { @Entry(category = "1|List objects", //
                label = "1|with outgoing references", //
                icon = "/META-INF/icons/list_outbound.gif"), //

                @Entry(category = "1|List objects", //
                label = "2|with incoming references", //
                options = "-inbound", //
                icon = "/META-INF/icons/list_inbound.gif") //
})
public class ObjectListQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(flag = "none")
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public boolean inbound = false;

    public IResult execute(IProgressListener listener) throws Exception
    {
        return inbound ? new ObjectListResult.Inbound(snapshot, objects.getIds(listener)) //
                        : new ObjectListResult.Outbound(snapshot, objects.getIds(listener));
    }

}
