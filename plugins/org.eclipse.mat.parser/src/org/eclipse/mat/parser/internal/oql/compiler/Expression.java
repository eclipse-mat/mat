/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;

public abstract class Expression
{

    abstract public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException;

    abstract public boolean isContextDependent(EvaluationContext ctx);

}
