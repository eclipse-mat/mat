package org.eclipse.mat.snapshot.extension;

import java.util.List;

/**
 * @since 1.6
 */
public interface ICollectionExtractorProvider
{
    List<CollectionExtractionInfo> getExtractorInfo();
}
