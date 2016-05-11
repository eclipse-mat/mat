/*******************************************************************************
 * Copyright (c) 2015, 2016 James Livingston
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    James Livingston - initial implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.extension;

import java.util.List;

/**
 * @since 1.6
 */
public interface ICollectionExtractorProvider
{
    List<CollectionExtractionInfo> getExtractorInfo();
}
