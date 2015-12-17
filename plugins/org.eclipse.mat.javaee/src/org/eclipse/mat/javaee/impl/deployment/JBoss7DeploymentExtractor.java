package org.eclipse.mat.javaee.impl.deployment;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.Utils;
import org.eclipse.mat.javaee.deployment.api.DeploymentExtractor;
import org.eclipse.mat.javaee.deployment.api.DeploymentType;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IClassLoader;
import org.eclipse.mat.snapshot.model.IObject;

public class JBoss7DeploymentExtractor implements DeploymentExtractor
{
    // TODO: 'parent' field

    public String getDeploymentName(IObject deployment)
    {
        try {
            return ((IObject)deployment.resolveValue("name")).getClassSpecificName();
        } catch (SnapshotException e) {
            JavaEEPlugin.warning("Unable to resolve URI", e);
            return null;
        }
    }

    public DeploymentType getDeploymentType(IObject deployment)
    {
        try {
            IObject attachment = getAttachment(deployment, DEPLOYMENT_TYPE);
            if (attachment == null) {
                JavaEEPlugin.warning("Unable to determine deployment type for " + deployment.getDisplayName());
                return DeploymentType.UKNOWN;
            }

            String deploymentType = ((IObject) attachment.resolveValue("name")).getClassSpecificName();

            if (deploymentType.equals("EAR"))
                return DeploymentType.EAR;
            else if (deploymentType.equals("WAR"))
                return DeploymentType.WAR;
            else if (deploymentType.equals("EJB_JAR"))
                return DeploymentType.JAR_EJB;
            else if (deploymentType.equals("APPLICATION_CLIENT"))
                return DeploymentType.APPLICATION_CLIENT;
            else {
                JavaEEPlugin.warning("Unknown determine deployment type " + deploymentType + " for " + deployment.getDisplayName());
                return DeploymentType.UKNOWN;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error("Unable to access deployment type", e);
            return null;
        }
    }


    public IClassLoader getClassloader(IObject deployment) {
        try {
            IObject attachment = getAttachment(deployment, MODULE);
            return  (attachment != null) ? (IClassLoader) attachment.resolveValue("moduleClassLoader") : null;
        } catch (SnapshotException e) {
            JavaEEPlugin.error("Unable to access deployment classloader", e);
            return null;
        }
    }


    private IObject getAttachment(IObject deployment, AttachmentKey key) throws SnapshotException {
        ExtractedMap attachments = CollectionExtractionUtils.extractMap((IObject) deployment.resolveValue("attachments"));
        Collection<IObject> attachmentKey = findAttachmentKey(deployment.getSnapshot(), key);
        for (IObject ak: attachmentKey) {
            IObject attachment = attachments.getByKeyIdentity(ak);
            if (attachment != null)
                return attachment;
        }
        return null;
    }

    private Collection<IObject> findAttachmentKey(ISnapshot snapshot, AttachmentKey key) throws SnapshotException {
        ArrayList<IObject> attachmentKeys = new ArrayList<IObject>(1);
        Collection<IClass> classes = snapshot.getClassesByName(key.klass, false);
        if (classes != null) {
            for (IClass klass: classes) {
                IObject ak = Utils.findStaticObjectField(klass, key.field);
                if (ak != null)
                    attachmentKeys.add(ak);
            }
        }
        return attachmentKeys;
    }

    private static final String EE_ATTACHMENTS_CLASS = "org.jboss.as.ee.structure.Attachments";
    private static final String DEPLOYMENT_ATTACHMENTS_CLASS = "org.jboss.as.server.deployment.Attachments";
    private static final AttachmentKey DEPLOYMENT_TYPE = new AttachmentKey(EE_ATTACHMENTS_CLASS, "DEPLOYMENT_TYPE");
    private static final AttachmentKey MODULE = new AttachmentKey(DEPLOYMENT_ATTACHMENTS_CLASS, "MODULE");

    private static class AttachmentKey {
        public final String klass;
        public final String field;

        public AttachmentKey(String klass, String field)
        {
            this.klass = klass;
            this.field = field;
        }

        public String toString() {
            return "AttachmentKey: " + klass + "." + field;
        }
    }
}
