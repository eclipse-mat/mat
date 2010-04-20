/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.snapshot;

import java.util.Collection;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.descriptors.IArgumentDescriptor;
import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.tests.TestSnapshots;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Test;

public class QueryLookupTest
{
	@Test
	public void testLookup() throws SnapshotException
	{
		String queryId = "histogram";
		String argumentName = "objects";
		ISnapshot snapshot = TestSnapshots.getSnapshot(TestSnapshots.SUN_JDK6_18_32BIT, false);
		
		Collection<IClass> classes = snapshot.getClassesByName("java.lang.String", false);
		int[] objectIDs = classes.iterator().next().getObjectIds();
		
		SnapshotQuery query = SnapshotQuery.lookup(queryId, snapshot);
		
		IAnnotatedObjectDescriptor queryDescriptor = query.getDescriptor();
		assert queryDescriptor != null : "query.getDescriptor() shouldn't return null";
		
		assert queryDescriptor.getIdentifier() != null : "query.getDescriptor().getIdentifier() shouldn't return null";
		assert queryDescriptor.getIdentifier().equals(queryId) : "query.getDescriptor().getIdentifier() must be equal to " + queryId;

		assert queryDescriptor.getHelp() != null && queryDescriptor.getHelp().length() > 0 : "Help for query " + queryId + " shouldn't be empty";

		assert queryDescriptor.getIcon() != null : "Icon for query " + queryId + " shouldn't be null";
		
		assert queryDescriptor.getName() != null && queryDescriptor.getName().length() > 0 : "Name for query " + queryId + " shouldn't be empty";

		List<? extends IArgumentDescriptor> arguments = query.getArguments();
		boolean foundObjectsArg = false;
		for (IArgumentDescriptor iArgumentDescriptor : arguments)
		{
			if (iArgumentDescriptor.getName().equals(argumentName))
			{
				foundObjectsArg = true;
				break;
			}
		}
		assert foundObjectsArg : "Could not find an argument named " + argumentName + " for query " + queryId;
		
		query.setArgument(argumentName, objectIDs);
		IResult result = query.execute(new VoidProgressListener());
		
		assert result != null : "The " + queryId + " query must return a non-null result";
	}

}
