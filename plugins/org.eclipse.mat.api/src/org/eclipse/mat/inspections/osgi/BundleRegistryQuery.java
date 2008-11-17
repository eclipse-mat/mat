/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.osgi;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.osgi.model.Bundle;
import org.eclipse.mat.inspections.osgi.model.BundleDescriptor;
import org.eclipse.mat.inspections.osgi.model.BundleFragment;
import org.eclipse.mat.inspections.osgi.model.BundleReaderFactory;
import org.eclipse.mat.inspections.osgi.model.IBundleReader;
import org.eclipse.mat.inspections.osgi.model.OSGiModel;
import org.eclipse.mat.inspections.osgi.model.Service;
import org.eclipse.mat.inspections.osgi.model.Service.ServiceProperty;
import org.eclipse.mat.inspections.osgi.model.eclipse.ConfigurationElement;
import org.eclipse.mat.inspections.osgi.model.eclipse.Extension;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;
import org.eclipse.mat.inspections.osgi.model.eclipse.ConfigurationElement.PropertyPair;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("Equinox Bundle Explorer (beta)")
@CommandName("bundle_registry")
@Category("Eclipse")
@Icon("/META-INF/icons/osgi/registry.gif")
public class BundleRegistryQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public BringToTop topLevelBy = BringToTop.NONE;

    public enum BringToTop
    {
        NONE("Bundles", Icons.BUNDLE), //
        BY_SERVICE("Services", Icons.SERVICE), //
        BY_EXTENSION_POINT("Extension Points", Icons.EXTENSION_POINTS);

        String label;
        URL icon;

        private BringToTop(String label, URL icon)
        {
            this.label = label;
            this.icon = icon;
        }

        public URL getIcon()
        {
            return icon;
        }

        public String toString()
        {
            return label;
        }

    }

    public IResult execute(IProgressListener listener) throws Exception
    {
        IBundleReader bundleReader = BundleReaderFactory.getBundleReader(snapshot);
        if (bundleReader == null)
            return new TextResult("Only Equinox OSGi Framework is supported");

        OSGiModel model = bundleReader.readOSGiModel(listener);
        return create(model, listener);
    }

    private BundleTreeResult create(OSGiModel model, IProgressListener listener) throws SnapshotException
    {
        if (topLevelBy == null)
            topLevelBy = BringToTop.NONE;

        switch (topLevelBy)
        {
            case NONE:
                return Factory.create(model);
            case BY_SERVICE:
                return Factory.servicesOnTop(model);
            case BY_EXTENSION_POINT:
                return Factory.extensionPointsOnTop(model);

        }

        return null;
    }

    public static class Factory
    {
        public static BundleTreeResult create(OSGiModel model) throws SnapshotException
        {
            return new BundleTreeResult(model);
        }

        public static BundleTreeResult servicesOnTop(OSGiModel model)
        {
            return new ServicesTreeResult(model);
        }

        public static BundleTreeResult extensionPointsOnTop(OSGiModel model) throws SnapshotException
        {
            return new ExtensionTreeResult(model);
        }

    }

    interface Icons
    {
        URL BUNDLE = BundleTreeResult.class.getResource("/META-INF/icons/osgi/bundle.gif");
        URL LOCATION = BundleTreeResult.class.getResource("/META-INF/icons/osgi/location.gif");
        URL DEPENDENTS = BundleTreeResult.class.getResource("/META-INF/icons/osgi/dependents.gif");
        URL DEPENDENCIES = BundleTreeResult.class.getResource("/META-INF/icons/osgi/dependencies.gif");
        URL USED_SERVICES = BundleTreeResult.class.getResource("/META-INF/icons/osgi/used_services.gif");
        URL REGISTERED_SERVICES = BundleTreeResult.class.getResource("/META-INF/icons/osgi/registered_services.gif");
        URL SERVICE = BundleTreeResult.class.getResource("/META-INF/icons/osgi/int_obj.gif");
        URL EXTENSION_POINTS = BundleTreeResult.class.getResource("/META-INF/icons/osgi/ext_points_obj.gif");
        URL EXTENSION_POINT = BundleTreeResult.class.getResource("/META-INF/icons/osgi/ext_point_obj.gif");
        URL EXTENSIONS = BundleTreeResult.class.getResource("/META-INF/icons/osgi/extensions_obj.gif");
        URL EXTENSION = BundleTreeResult.class.getResource("/META-INF/icons/osgi/extension_obj.gif");
        URL PROPERTY_PAIR = BundleTreeResult.class.getResource("/META-INF/icons/osgi/attr_xml_obj.gif");
        URL ATTRIBUTE = BundleTreeResult.class.getResource("/META-INF/icons/osgi/generic_xml_obj.gif");
        URL FRAGMENTS = BundleTreeResult.class.getResource("/META-INF/icons/osgi/frgmts_obj.gif");
        URL FRAGMENT = BundleTreeResult.class.getResource("/META-INF/icons/osgi/frgmt_obj.gif");
        URL PROPERTY = BundleTreeResult.class.getResource("/META-INF/icons/osgi/property.gif");
    }

    private static class Folder
    {
        String label;
        Bundle bundle;
        Type type;

        public Folder(Bundle bundle, String label, Type type)
        {
            this.bundle = bundle;
            this.label = label;
            this.type = type;
        }

    }

    private static class DescriptorFolder extends Folder
    {
        BundleDescriptor descriptor;

        public DescriptorFolder(BundleDescriptor descriptor, String label, Type type)
        {
            super(null, label, type);
            this.descriptor = descriptor;

        }

    }

    private static class ExtensionFolder extends Folder
    {
        Extension extension;

        ExtensionFolder(Extension extension, String label, Type type)
        {
            super(null, label, type);
            this.extension = extension;
        }
    }

    private static class PropertiesFolder extends Folder
    {
        Service service;

        PropertiesFolder(Service service, String label, Type type)
        {
            super(null, label, type);
            this.service = service;
        }
    }

    enum Type
    {
        LOCATION, EXTENSIONS, EXTENSION_POINTS, DEPENDENCIES, DEPENDENTS, REGISTERED_SERVICES, SERVICES_IN_USE, CONTRIBUTED_BY, FRAGMENTS, HOST, PROPERTIES, BUNDLES_USING, BUNDLE
    };

    public static class BundleTreeResult implements IResultTree, IIconProvider
    {

        protected OSGiModel model;
        protected BundleRegistryQuery.BringToTop topLevelBy;

        public BundleTreeResult(OSGiModel model)
        {
            this.model = model;
        }

        public OSGiModel getModel()
        {
            return model;
        }

        public List<?> getChildren(Object parent)
        {
            if (parent instanceof BundleDescriptor)
            {
                BundleDescriptor descriptor = (BundleDescriptor) parent;
                Bundle bundle;
                try
                {
                    bundle = model.getBundle(descriptor);
                }
                catch (SnapshotException e)
                {
                    MATPlugin.log(e, "Failed reading bundle from OSGiModel");
                    return null;
                }
                List<Object> children = new ArrayList<Object>(2);
                if (bundle.getLocation() != null)
                    children.add(new Folder(bundle, bundle.getLocation(), Type.LOCATION));
                if (bundle.getDependencies() != null && !bundle.getDependencies().isEmpty())
                    children.add(new Folder(bundle, "Dependencies", Type.DEPENDENCIES));
                if (bundle.getDependents() != null && !bundle.getDependents().isEmpty())
                    children.add(new Folder(bundle, "Dependents", Type.DEPENDENTS));
                if (bundle.getExtentionPoints() != null && !bundle.getExtentionPoints().isEmpty())
                    children.add(new Folder(bundle, "Extension Points", Type.EXTENSION_POINTS));
                if (bundle.getExtentions() != null && !bundle.getExtentions().isEmpty())
                    children.add(new Folder(bundle, "Extensions", Type.EXTENSIONS));
                if (bundle.getRegisteredServices() != null && !bundle.getRegisteredServices().isEmpty())
                    children.add(new Folder(bundle, "Registered Services", Type.REGISTERED_SERVICES));
                if (bundle.getUsedServices() != null && !bundle.getUsedServices().isEmpty())
                    children.add(new Folder(bundle, "Used Services", Type.SERVICES_IN_USE));
                if (bundle.getFragments() != null && !bundle.getFragments().isEmpty())
                    children.add(new Folder(bundle, "Fragments", Type.FRAGMENTS));
                if (bundle instanceof BundleFragment && ((BundleFragment) bundle).getHost() != null)
                    children.add(new Folder(bundle,
                                    "hosted by: " + ((BundleFragment) bundle).getHost().getBundleName(), Type.HOST));

                return children;
            }
            else if (parent instanceof Folder)
            {
                Folder folder = (Folder) parent;
                switch (folder.type)
                {
                    case DEPENDENCIES:
                        return folder.bundle.getDependencies();
                    case DEPENDENTS:
                        return folder.bundle.getDependents();
                    case EXTENSION_POINTS:
                        return folder.bundle.getExtentionPoints();
                    case EXTENSIONS:
                        return folder.bundle.getExtentions();
                    case REGISTERED_SERVICES:
                        return folder.bundle.getRegisteredServices();
                    case SERVICES_IN_USE:
                        return folder.bundle.getUsedServices();
                    case CONTRIBUTED_BY:
                        return ((ExtensionFolder) folder).extension.getConfigurationElements();
                    case FRAGMENTS:
                        return folder.bundle.getFragments();
                    case HOST:
                        return getChildren(((BundleFragment) folder.bundle).getHost());
                }

            }
            else if (parent instanceof ExtensionPoint)
            {
                ExtensionPoint point = (ExtensionPoint) parent;
                List<Extension> extensions = point.getExtensions();
                List<Object> children = new ArrayList<Object>(extensions.size());
                for (Extension extension : extensions)
                {
                    String bundleName = (extension.getContributedBy() != null) ? extension.getContributedBy()
                                    .getBundleName() : "";
                    children.add(new ExtensionFolder(extension, "contributed by: " + bundleName, Type.CONTRIBUTED_BY));
                }
                return children;
            }
            else if (parent instanceof Extension)
            {
                return ((Extension) parent).getConfigurationElements();
            }
            else if (parent instanceof ConfigurationElement)
            {
                // return both properties and other congifElements if available
                List<Object> children = new ArrayList<Object>();
                children.addAll(((ConfigurationElement) parent).getPropertiesAndValues());
                List<ConfigurationElement> configElements = ((ConfigurationElement) parent).getConfigurationElements();
                if (configElements != null)
                    children.addAll(configElements);

                return children;
            }
            else if (parent instanceof Service)
                return ((Service) parent).getProperties();

            return null;
        }

        public List<?> getElements()
        {
            return model.getBundleDescriptors();
        }

        public boolean hasChildren(Object element)
        {
            if (element instanceof BundleDescriptor || element instanceof Extension)
                return true;
            else if (element instanceof ExtensionPoint)
                return !((ExtensionPoint) element).getExtensions().isEmpty();
            else if (element instanceof ConfigurationElement)
                return !((ConfigurationElement) element).getConfigurationElements().isEmpty()
                                || !((ConfigurationElement) element).getPropertiesAndValues().isEmpty();
            else if (element instanceof Service)
                return ((Service) element).getProperties() != null;
            else if (element instanceof Folder)
            {
                switch (((Folder) element).type)
                {
                    case LOCATION:
                        return false;
                }
                return true;
            }

            return false;
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column("Bundles"),//
                            new Column("State").noTotals() };

        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            switch (columnIndex)
            {
                case 0:
                    if (row instanceof BundleDescriptor)
                        return ((BundleDescriptor) row).getBundleName();
                    if (row instanceof Folder)
                        return ((Folder) row).label;
                    if (row instanceof Service)
                        return ((Service) row).getName();
                    if (row instanceof ExtensionPoint)
                        return ((ExtensionPoint) row).getName();
                    if (row instanceof Extension)
                        return ((Extension) row).getName();
                    if (row instanceof ConfigurationElement)
                        return ((ConfigurationElement) row).getName();
                    if (row instanceof PropertyPair)
                        return ((PropertyPair) row).property + " = " + ((PropertyPair) row).value;
                    if (row instanceof ServiceProperty)
                        return ((ServiceProperty) row).property + " = " + ((ServiceProperty) row).value;
                case 1:
                    if (row instanceof BundleDescriptor)
                        return ((BundleDescriptor) row).getState();
                    if (row instanceof Folder && ((Folder) row).type.equals(Type.HOST))
                        return ((BundleFragment) ((Folder) row).bundle).getHost().getState();
            }
            return null;
        }

        public IContextObject getContext(final Object row)
        {
            if (row instanceof ExtensionFolder)
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return ((ExtensionFolder) row).extension.getObjectId();
                    }
                };
            if (row instanceof Folder && ((Folder) row).type.equals(Type.HOST))
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return ((BundleFragment) ((Folder) row).bundle).getHost().getObjectId();
                    }
                };
            else if (row instanceof DescriptorFolder)
                return new IContextObject()
                {
                    public int getObjectId()
                    {
                        return ((DescriptorFolder) row).descriptor.getObjectId();
                    }
                };
            if (row instanceof Folder || row instanceof PropertyPair || row instanceof ServiceProperty)
                return null;
            return new IContextObject()
            {
                public int getObjectId()
                {
                    if (row instanceof BundleDescriptor)
                        return ((BundleDescriptor) row).getObjectId();
                    if (row instanceof Service)
                        return ((Service) row).getObjectId();
                    if (row instanceof ExtensionPoint)
                        return ((ExtensionPoint) row).getObjectId();
                    if (row instanceof Extension)
                        return ((Extension) row).getObjectId();
                    if (row instanceof ConfigurationElement)
                        return ((ConfigurationElement) row).getObjectId();
                    return -1;
                }
            };
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public URL getIcon(Object row)
        {
            if (row instanceof BundleDescriptor)
            {
                if (((BundleDescriptor) row).getType().equals(BundleDescriptor.Type.FRAGMENT))
                    return Icons.FRAGMENT;
                return Icons.BUNDLE;
            }
            if (row instanceof Folder)
            {
                switch (((Folder) row).type)
                {
                    case LOCATION:
                        return Icons.LOCATION;
                    case DEPENDENCIES:
                        return Icons.DEPENDENCIES;
                    case DEPENDENTS:
                        return Icons.DEPENDENTS;
                    case SERVICES_IN_USE:
                        return Icons.USED_SERVICES;
                    case REGISTERED_SERVICES:
                        return Icons.REGISTERED_SERVICES;
                    case EXTENSION_POINTS:
                        return Icons.EXTENSION_POINTS;
                    case EXTENSIONS:
                        return Icons.EXTENSIONS;
                    case CONTRIBUTED_BY:
                        return Icons.EXTENSION;
                    case FRAGMENTS:
                        return Icons.FRAGMENTS;
                    case HOST:
                        return Icons.BUNDLE;
                    case BUNDLE:
                        return Icons.BUNDLE;

                }
            }
            if (row instanceof Service)
                return Icons.SERVICE;
            if (row instanceof ExtensionPoint)
                return Icons.EXTENSION_POINT;
            if (row instanceof Extension)
                return Icons.EXTENSION;
            if (row instanceof ConfigurationElement)
                return Icons.ATTRIBUTE;
            if (row instanceof PropertyPair)
                return Icons.PROPERTY_PAIR;
            if (row instanceof ServiceProperty)
                return Icons.PROPERTY;
            return null;
        }

        public BundleRegistryQuery.BringToTop getTopLevelBy()
        {
            return BringToTop.NONE;
        }

    }

    public static class ServicesTreeResult extends BundleTreeResult
    {

        public ServicesTreeResult(OSGiModel model)
        {
            super(model);
        }

        @Override
        public List<?> getChildren(Object parent)
        {
            if (parent instanceof Service)
            {
                Service service = (Service) parent;
                List<Object> children = new ArrayList<Object>(2);
                if (service.getProperties() != null)
                    children.add(new PropertiesFolder(service, "Properties", Type.PROPERTIES));
                children.add(new DescriptorFolder(service.getBundleDescriptor(), "registered by: "
                                + service.getBundleDescriptor().getBundleName(), Type.BUNDLE));
                if (service.getBundlesUsing() != null && !service.getBundlesUsing().isEmpty())
                    children.add(new PropertiesFolder(service, "Bundles Using", Type.BUNDLES_USING));
                return children;
            }

            else if (parent instanceof Folder)
            {
                Folder folder = (Folder) parent;
                switch (folder.type)
                {
                    case PROPERTIES:
                        return ((PropertiesFolder) folder).service.getProperties();
                    case BUNDLES_USING:
                        return ((PropertiesFolder) folder).service.getBundlesUsing();
                    case BUNDLE:
                        return super.getChildren(((DescriptorFolder) folder).descriptor);
                }

            }

            return super.getChildren(parent);
        }

        @Override
        public List<?> getElements()
        {
            return model.getServices();
        }

        @Override
        public URL getIcon(Object row)
        {
            if (row instanceof Folder)
            {
                switch (((Folder) row).type)
                {
                    case PROPERTIES:
                        return Icons.PROPERTY;
                    case BUNDLES_USING:
                        return Icons.DEPENDENTS;
                }
            }
            return super.getIcon(row);
        }

        @Override
        public boolean hasChildren(Object element)
        {
            if (element instanceof Service)
                return ((Service) element).getProperties() != null || ((Service) element).getBundleDescriptor() != null;
            return super.hasChildren(element);
        }

        @Override
        public BundleRegistryQuery.BringToTop getTopLevelBy()
        {
            return BringToTop.BY_SERVICE;
        }

    }

    public static class ExtensionTreeResult extends BundleTreeResult
    {

        public ExtensionTreeResult(OSGiModel model)
        {
            super(model);
        }

        @Override
        public List<?> getChildren(Object parent)
        {
            if (parent instanceof ExtensionPoint)
            {
                ExtensionPoint point = (ExtensionPoint) parent;
                List<Extension> extensions = point.getExtensions();
                List<Object> children = new ArrayList<Object>(extensions.size());
                for (Extension extension : extensions)
                {
                    if (extension.getContributedBy() != null)
                        children.add(new ExtensionFolder(extension, "contributed by: "
                                        + extension.getContributedBy().getBundleName(), Type.CONTRIBUTED_BY));
                }
                if (point.getContributedBy() != null)
                    children.add(new DescriptorFolder(point.getContributedBy(), "registered by: "
                                    + point.getContributedBy().getBundleName(), Type.BUNDLE));

                return children;
            }

            else if (parent instanceof Folder)
            {
                Folder folder = (Folder) parent;
                switch (folder.type)
                {
                    case BUNDLE:
                        return super.getChildren(((DescriptorFolder) folder).descriptor);
                }

            }

            return super.getChildren(parent);
        }

        @Override
        public List<?> getElements()
        {
            return model.getExtensionPoints();
        }

        @Override
        public boolean hasChildren(Object element)
        {
            if (element instanceof ExtensionPoint)
                return ((ExtensionPoint) element).getContributedBy() != null
                                || !((ExtensionPoint) element).getExtensions().isEmpty();
            return super.hasChildren(element);
        }

        @Override
        public BundleRegistryQuery.BringToTop getTopLevelBy()
        {
            return BringToTop.BY_EXTENSION_POINT;
        }

    }

}
