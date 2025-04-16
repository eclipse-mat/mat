package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ExcludedReferencesDescriptor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

public interface IObjectMarker
{

    int markSingleThreaded();

    void markMultiThreaded(int threads) throws InterruptedException;

    int markSingleThreaded(ExcludedReferencesDescriptor[] excludeSets, ISnapshot snapshot)
                    throws SnapshotException, IProgressListener.OperationCanceledException;

}
