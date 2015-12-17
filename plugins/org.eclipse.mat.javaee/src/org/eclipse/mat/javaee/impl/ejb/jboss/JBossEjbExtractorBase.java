package org.eclipse.mat.javaee.impl.ejb.jboss;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class JBossEjbExtractorBase {
    public String getComponentName(IObject component) {
        try {
            return ((IObject)component.resolveValue("componentName")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getModuleName(IObject component) {
        try {
            return ((IObject)component.resolveValue("moduleName")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getApplicationName(IObject component) {
        try {
            return ((IObject)component.resolveValue("applicationName")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public String getDistinctName(IObject component) {
        try {
            return ((IObject)component.resolveValue("distinctName")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public IClass getComponentClass(IObject component) {
        try {
            return (IClass)component.resolveValue("componentClass");
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }
}