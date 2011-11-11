/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * (Optionally) annotates the command name of the query. If not provided, the
 * name of the query is changed to lower case letters and spaces are replaced by
 * underscore. This name is used in the Query Browser command line,
 * as the command name in the tab of a query and in
 * {@link org.eclipse.mat.snapshot.query.SnapshotQuery#lookup(String, org.eclipse.snapshot.ISnapshot)}.
 */
@Target( { TYPE })
@Retention(RUNTIME)
public @interface CommandName
{
    String value();
}
