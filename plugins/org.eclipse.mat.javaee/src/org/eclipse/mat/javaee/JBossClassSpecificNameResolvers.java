package org.eclipse.mat.javaee;

import java.util.Iterator;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.PrettyPrinter;
import org.eclipse.mat.snapshot.registry.ClassSpecificNameResolverRegistry;

public class JBossClassSpecificNameResolvers
{
    public static final int LENGTH_LIMIT = 1024;

    @Subject("org.jboss.as.controller.PathAddress")
    public static class PathAddressNameResolver implements IClassSpecificNameResolver {
        public String resolve(IObject address) throws SnapshotException
        {
            IObject list = (IObject) address.resolveValue("pathAddressList");
            StringBuffer sb = new StringBuffer();

            Iterator<IObject> it = CollectionExtractionUtils.extractList(list).iterator(); // org.jboss.as.controller.PathElements
            while (it.hasNext()) {
                // limit length
                if (sb.length() >= LENGTH_LIMIT)
                    return sb.append("...").toString();

                IObject element = it.next();
                sb.append(PathElementNameResolver.resolve_(element));
                if (it.hasNext())
                    sb.append('/');
            }

            return sb.toString();
        }
    }

    @Subject("org.jboss.as.controller.PathElement")
    public static class PathElementNameResolver implements IClassSpecificNameResolver
    {
        public static String resolve_(IObject object) throws SnapshotException {
            IObject key = (IObject) object.resolveValue("key");
            IObject value = (IObject) object.resolveValue("value");
            return key.getClassSpecificName() + "=" + value.getClassSpecificName();
        }

        public String resolve(IObject object) throws SnapshotException
        {
            return resolve_(object);
        }
    }

    @Subject("org.jboss.as.controller.services.path.PathEntry")
    public static class PathEntryNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject name = (IObject) object.resolveValue("name");
            IObject path = (IObject) object.resolveValue("path");
            return name.getClassSpecificName() + "=" + path.getClassSpecificName();
        }
    }


    // DMR types

    @Subject("org.jboss.dmr.StringModelValue")
    public static class StringModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            return PrettyPrinter.objectAsString((IObject) object.resolveValue("value"), LENGTH_LIMIT);
        }
    }

    @Subjects({"org.jboss.dmr.IntModelValue",
               "org.jboss.dmr.LongModelValue",
               "org.jboss.dmr.DoubleModelValue",
               "org.jboss.dmr.BooleanModelValue"})
    public static class NumberModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            return object.resolveValue("value").toString();
        }
    }

    @Subject("org.jboss.dmr.ModelType")
    public static class TypeModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            return ((IObject)object.resolveValue("name")).getClassSpecificName();
        }
    }

    @Subjects({"org.jboss.dmr.ModelNode",
               "org.jboss.dmr.BigDecimalModelValue",
               "org.jboss.dmr.BigIntegerModelValue",
               "org.jboss.dmr.BytesModelValue",
               "org.jboss.dmr.TypeModelValue",
    })
    public static class ValueFieldChainedNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject value = (IObject) object.resolveValue("value");
            return value != null ? ClassSpecificNameResolverRegistry.resolve(value) : null;
        }
    }

    @Subject("org.jboss.dmr.ListModelValue")
    public static class ListModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            ExtractedCollection list = CollectionExtractionUtils.extractList((IObject) object.resolveValue("list"));
            StringBuffer sb = new StringBuffer("[");

            Iterator<IObject> it = list.iterator();
            while (it.hasNext()) {
                // limit length
                if (sb.length() >= LENGTH_LIMIT)
                    return sb.append("...]").toString();

                IObject element = it.next();
                sb.append(element.getClassSpecificName());
                if (it.hasNext())
                    sb.append(", ");
            }
            return sb.append("]").toString();
        }
    }

    @Subject("org.jboss.dmr.ObjectModelValue")
    public static class ObjectModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            ExtractedMap map = CollectionExtractionUtils.extractMap((IObject) object.resolveValue("map"));
            StringBuffer sb = new StringBuffer("{");

            Iterator<Map.Entry<IObject, IObject>> it = map.iterator();
            while (it.hasNext()) {
                // limit length
                if (sb.length() >= LENGTH_LIMIT)
                    return sb.append("...}").toString();

                Map.Entry<IObject, IObject> entry = it.next();
                sb.append(entry.getKey().getClassSpecificName() + "=" + entry.getValue().getClassSpecificName());
                if (it.hasNext())
                    sb.append(", ");
            }
            return sb.append("}").toString();
        }
    }

    @Subject("org.jboss.dmr.PropertyModelValue")
    public static class PropertyModelValueNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject value = (IObject) object.resolveValue("property");
            return ClassSpecificNameResolverRegistry.resolve(value);
        }
    }

    @Subject("org.jboss.dmr.Property")
    public static class PropertyNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject name = (IObject) object.resolveValue("name");
            IObject value = (IObject) object.resolveValue("value");
            return name.getClassSpecificName() + "=" + ClassSpecificNameResolverRegistry.resolve(value);
        }
    }

    // modules

    @Subject("org.jboss.modules.ModuleClassLoader")
    public static class ModuleClassLoaderNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject module = (IObject) object.resolveValue("module");
            return ClassSpecificNameResolverRegistry.resolve(module);
        }
    }

    @Subject("org.jboss.modules.Module")
    public static class ModuleNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject identifier = (IObject) object.resolveValue("identifier");
            return ClassSpecificNameResolverRegistry.resolve(identifier);
        }
    }

    @Subject("org.jboss.modules.ModuleIdentifier")
    public static class ModuleIdentifierNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject name = (IObject) object.resolveValue("name");
            IObject slot = (IObject) object.resolveValue("slot");
            return "Module " + name.getClassSpecificName() + "=" + slot.getClassSpecificName();
        }
    }

    // logging

    @Subject("org.jboss.logmanager.LoggerNode")
    public static class LoggerNodeNameResolver implements IClassSpecificNameResolver
    {
        public String resolve(IObject object) throws SnapshotException
        {
            return "Logger " + ((IObject) object.resolveValue("fullName")).getClassSpecificName();
        }
    }
    
    // MSC
    @Subject("org.jboss.msc.service.ServiceName")
    public static class MscServiceNameNameResolver implements IClassSpecificNameResolver {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject nameObj = (IObject) object.resolveValue("name");
            String name = nameObj.getClassSpecificName();

            IObject parent = (IObject) object.resolveValue("parent");
            return (parent != null) ? resolve(parent) + "." + name : name;
        }
    }
    @Subjects({"org.jboss.msc.service.ServiceController$Mode",
               "org.jboss.msc.service.ServiceController$Substate",
    })
    public static class EnumNameResolver implements IClassSpecificNameResolver {
        public String resolve(IObject object) throws SnapshotException
        {
            IObject name =  (IObject) object.resolveValue("name");
            return name.getClassSpecificName();
        }
    }
}
