/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation.
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
/**
 * Code for reading data from collections found in a snapshot.
 * This is used to hide the differences in implementation of collection classes
 * generating the collections in the snapshot.
 * Queries should use the {@link org.eclipse.mat.inspections.collectionextract} package
 * to read collections in the snapshot.
 * This is an internal package not intended as an Application Programming Interface (API).
*/
package org.eclipse.mat.internal.collectionextract;
