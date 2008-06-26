package org.eclipse.mat.query.registry;

import org.eclipse.mat.SnapshotException;

public interface ArgumentFactory
{
    Object build(ArgumentDescriptor descriptor) throws SnapshotException;

    void appendUsage(StringBuilder buf);
}
