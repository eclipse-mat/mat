package org.eclipse.mat.javaee.jboss.api;

import org.eclipse.mat.snapshot.model.IObject;

public interface JBossMscExtractor
{
    String getServiceName(IObject request);
    String getMode(IObject request);
    String getState(IObject request);
}
