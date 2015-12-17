package org.eclipse.mat.javaee.impl.deployment;

import java.util.Collection;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.deployment.api.DeploymentExtractor;
import org.eclipse.mat.javaee.deployment.api.DeploymentType;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;

public class JBoss5DeploymentExtractor implements DeploymentExtractor
{
    public static ArrayInt findDeployments(ISnapshot snapshot) throws SnapshotException
    {
        ArrayInt results = new ArrayInt();
        Collection<IClass> classes = snapshot.getClassesByName("org.jboss.deployers.plugins.main.MainDeployerImpl", false);
        if (classes == null)
            return results;

        for (IClass klass: classes) {
            for (int objId: klass.getObjectIds()) {
                IObject deployer = snapshot.getObject(objId);
                IObject deployments = (IObject) deployer.resolveValue("allDeployments");
                if (deployments != null) {
                    for (Map.Entry<IObject, IObject> e: CollectionExtractionUtils.extractMap(deployments)) {
                        // String -> org.jboss.deployers.vfs.plugins.structure.AbstractVFSDeploymentContext
                        results.add(e.getValue().getObjectId());
                    }
                }
            }
        }
        return results;
    }


    public String getDeploymentName(IObject deployment) {
        try {
            //org.jboss.deployers.vfs.plugins.structure.AbstractVFSDeploymentContext
            return ((IObject)deployment.resolveValue("simpleName")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.error("Unable to extract deployment name", e);
            return null;
        }
    }

    public DeploymentType getDeploymentType(IObject deployment) {
        /*try {*/
            //org.jboss.deployers.vfs.plugins.structure.AbstractVFSDeploymentContext
            return null; // ((IObject)deployment.resolveValue("simpleName")).getClassSpecificName();
        /*} catch (SnapshotException e) {
            JavaEEPlugin.error("Unable to extract deployment type", e);
            return null;
        }*/
    }

    public IClassLoader getClassloader(IObject deployment) {
        try {
            //org.jboss.deployers.vfs.plugins.structure.AbstractVFSDeploymentContext
            return (IClassLoader)deployment.resolveValue("classLoader");
        } catch (SnapshotException e) {
            JavaEEPlugin.error("Unable to extract deployment classloader", e);
            return null;
        }
    }
}
