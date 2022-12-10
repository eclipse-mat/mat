/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.queries;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

/**
 * Run a command in the context of another snapshot.
 */
@Category("Test")
@CommandName("secondary")
@Help("Secondary snapshots")
public class Secondary implements IQuery
{
    @Argument(advice = Advice.SECONDARY_SNAPSHOT)
    public ISnapshot baseline;

    @Argument
    public String command;

    @Override
    public IResult execute(IProgressListener listener) throws SnapshotException
    {
        SnapshotQuery q = SnapshotQuery.parse(command, baseline);
        return q.execute(listener);
    }
}
