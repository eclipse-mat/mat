/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - icon load changes
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
import org.eclipse.mat.inspections.osgi.model.eclipse.ConfigurationElement.PropertyPair;
import org.eclipse.mat.inspections.osgi.model.eclipse.Extension;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("bundle_registry")
@Icon("/META-INF/icons/osgi/registry.gif")
@Subjects({"org.eclipse.osgi.framework.internal.core.BundleRepository","org.eclipse.osgi.internal.framework.EquinoxBundle"})
@HelpUrl("/org.eclipse.mat.ui.help/tasks/bundleregistry.html")
public class BundleRegistryQuery implements IQuery
{
    @Argument
    public ISnapshot snapshot;

    @Argument(isMandatory = false)
    public Grouping groupBy = Grouping.NONE;

    public enum Grouping
    {
        NONE(Messages.BundleRegistryQuery_Bundles, Icons.BUNDLE), //
        BY_SERVICE(Messages.BundleRegistryQuery_Services, Icons.SERVICE), //
        BY_EXTENSION_POINT(Messages.BundleRegistryQuery_ExtensionPoints, Icons.EXTENSION_POINTS);

        String label;
        URL icon;

        private Grouping(String label, URL icon)
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
        OSGiModel model = bundleReader.readOSGiModel(listener);
        return create(model);
    }

    private BundleTreeResult create(OSGiModel model)
    {
        if (groupBy == null)
            groupBy = Grouping.NONE;

        switch (groupBy)
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
        public static BundleTreeResult create(OSGiModel model)
        {
            return new BundleTreeResult(model);
        }

        public static BundleTreeResult servicesOnTop(OSGiModel model)
        {
            return new ServicesTreeResult(model);
        }

        public static BundleTreeResult extensionPointsOnTop(OSGiModel model)
        {
            return new ExtensionTreeResult(model);
        }

    }

    interface Icons
    {
        URL BUNDLE = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/bundle.gif"); //$NON-NLS-1$
        URL LOCATION = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/location.gif"); //$NON-NLS-1$
        URL DEPENDENTS = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/dependents.gif"); //$NON-NLS-1$
        URL DEPENDENCIES = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/dependencies.gif"); //$NON-NLS-1$
        URL USED_SERVICES = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/used_services.gif"); //$NON-NLS-1$
        URL REGISTERED_SERVICES = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/registered_services.gif"); //$NON-NLS-1$
        URL SERVICE = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/int_obj.gif"); //$NON-NLS-1$
        URL EXTENSION_POINTS = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/ext_points_obj.gif"); //$NON-NLS-1$
        URL EXTENSION_POINT = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/ext_point_obj.gif"); //$NON-NLS-1$
        URL EXTENSIONS = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/extensions_obj.gif"); //$NON-NLS-1$
        URL EXTENSION = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/extension_obj.gif"); //$NON-NLS-1$
        URL PROPERTY_PAIR = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/attr_xml_obj.gif"); //$NON-NLS-1$
        URL ATTRIBUTE = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/generic_xml_obj.gif"); //$NON-NLS-1$
        URL FRAGMENTS = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/frgmts_obj.gif"); //$NON-NLS-1$
        URL FRAGMENT = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/frgmt_obj.gif"); //$NON-NLS-1$
        URL PROPERTY = org.eclipse.mat.snapshot.query.Icons.getURL("osgi/property.gif"); //$NON-NLS-1$
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
    }

    public static class BundleTreeResult implements IResultTree, IIconProvider
    {

        protected OSGiModel model;
        protected BundleRegistryQuery.Grouping topLevelBy;

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
                    MATPlugin.log(e, Messages.BundleRegistryQuery_ErrorMsg_FailedReadingModel);
                    return null;
                }
                List<Object> children = new ArrayList<Object>(2);
                if (bundle.getLocation() != null)
                    children.add(new Folder(bundle, bundle.getLocation(), Type.LOCATION));
                if (bundle.getDependencies() != null && !bundle.getDependencies().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_Dependencies, Type.DEPENDENCIES));
                if (bundle.getDependents() != null && !bundle.getDependents().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_Dependents, Type.DEPENDENTS));
                if (bundle.getExtentionPoints() != null && !bundle.getExtentionPoints().isEmpty())
                    children
                                    .add(new Folder(bundle, Messages.BundleRegistryQuery_ExtensionPoints,
                                                    Type.EXTENSION_POINTS));
                if (bundle.getExtentions() != null && !bundle.getExtentions().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_Extensions, Type.EXTENSIONS));
                if (bundle.getRegisteredServices() != null && !bundle.getRegisteredServices().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_RegisteredServices,
                                    Type.REGISTERED_SERVICES));
                if (bundle.getUsedServices() != null && !bundle.getUsedServices().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_UserServices, Type.SERVICES_IN_USE));
                if (bundle.getFragments() != null && !bundle.getFragments().isEmpty())
                    children.add(new Folder(bundle, Messages.BundleRegistryQuery_Fragments, Type.FRAGMENTS));
                if (bundle instanceof BundleFragment && ((BundleFragment) bundle).getHost() != null)
                    children.add(new Folder(bundle, MessageUtil.format(Messages.BundleRegistryQuery_HostedBy,
                                    ((BundleFragment) bundle).getHost().getBundleName()), Type.HOST));

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
                    case PROPERTIES:
                        return ((PropertiesFolder) folder).service.getProperties();
                    case BUNDLES_USING:
                        return ((PropertiesFolder) folder).service.getBundlesUsing();
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
                                    .getBundleName() : ""; //$NON-NLS-1$
                    children.add(new ExtensionFolder(extension, MessageUtil.format(
                                    Messages.BundleRegistryQuery_ContributedBy, bundleName), Type.CONTRIBUTED_BY));
                }
                return children;
            }
            else if (parent instanceof Extension)
            {
                return ((Extension) parent).getConfigurationElements();
            }
            else if (parent instanceof ConfigurationElement)
            {
                // return both properties and other configElements if available
                List<Object> children = new ArrayList<Object>();
                children.addAll(((ConfigurationElement) parent).getPropertiesAndValues());
                List<ConfigurationElement> configElements = ((ConfigurationElement) parent).getConfigurationElements();
                if (configElements != null)
                    children.addAll(configElements);

                return children;
            }
            else if (parent instanceof Service)
            {
                Service service = (Service) parent;
                List<Object> children = new ArrayList<Object>(2);
                if (service.getProperties() != null)
                    children
                                    .add(new PropertiesFolder(service, Messages.BundleRegistryQuery_Properties,
                                                    Type.PROPERTIES));
                if (service.getBundlesUsing() != null && !service.getBundlesUsing().isEmpty())
                    children.add(new PropertiesFolder(service, Messages.BundleRegistryQuery_BundlesUsing,
                                    Type.BUNDLES_USING));
                return children;
            }

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
            return new Column[] { new Column(Messages.BundleRegistryQuery_Bundles),//
                            new Column(Messages.BundleRegistryQuery_BundleState).noTotals() };

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
                        return ((PropertyPair) row).property + " = " + ((PropertyPair) row).value; //$NON-NLS-1$
                    if (row instanceof ServiceProperty)
                        return ((ServiceProperty) row).property + " = " + ((ServiceProperty) row).value; //$NON-NLS-1$
                case 1:
                    if (row instanceof BundleDescriptor)
                        return ((BundleDescriptor) row).getState();
                    if (row instanceof Folder && ((Folder) row).type.equals(Type.HOST))
                        return ((BundleFragment) ((Folder) row).bundle).getHost().getState();
                    if (row instanceof DescriptorFolder)
                        return ((DescriptorFolder) row).descriptor.getState();
                    if (row instanceof ExtensionFolder && ((ExtensionFolder)row).type == Type.CONTRIBUTED_BY)
                        return ((ExtensionFolder)row).extension.getContributedBy().getState();
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
                    case PROPERTIES:
                        return Icons.PROPERTY;
                    case BUNDLES_USING:
                        return Icons.DEPENDENTS;

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

        public BundleRegistryQuery.Grouping getGroupBy()
        {
            return Grouping.NONE;
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
                    children
                                    .add(new PropertiesFolder(service, Messages.BundleRegistryQuery_Properties,
                                                    Type.PROPERTIES));
                children.add(new DescriptorFolder(service.getBundleDescriptor(), MessageUtil.format(
                                Messages.BundleRegistryQuery_RegisteredBy, service.getBundleDescriptor()
                                                .getBundleName()), Type.BUNDLE));
                if (service.getBundlesUsing() != null && !service.getBundlesUsing().isEmpty())
                    children.add(new PropertiesFolder(service, Messages.BundleRegistryQuery_BundlesUsing,
                                    Type.BUNDLES_USING));
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
            return model.getServices();
        }

        @Override
        public boolean hasChildren(Object element)
        {
            if (element instanceof Service)
                return ((Service) element).getProperties() != null || ((Service) element).getBundleDescriptor() != null;
            return super.hasChildren(element);
        }

        @Override
        public BundleRegistryQuery.Grouping getGroupBy()
        {
            return Grouping.BY_SERVICE;
        }

        @Override
        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.BundleRegistryQuery_Services),//
                            new Column(Messages.BundleRegistryQuery_BundleState).noTotals() };
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
                if (point.getContributedBy() != null)
                    children.add(new DescriptorFolder(point.getContributedBy(), MessageUtil
                                    .format(Messages.BundleRegistryQuery_RegisteredBy, point.getContributedBy()
                                                    .getBundleName()), Type.BUNDLE));
                for (Extension extension : extensions)
                {
                    if (extension.getContributedBy() != null)
                        children.add(new ExtensionFolder(extension, MessageUtil.format(
                                        Messages.BundleRegistryQuery_ContributedBy, extension.getContributedBy()
                                                        .getBundleName()), Type.CONTRIBUTED_BY));
                }

                return children;
            }

            else if (parent instanceof Folder)
            {
                Folder folder = (Folder) parent;
                switch (folder.type)
                {
                    case BUNDLE:
                        return super.getChildren(((DescriptorFolder) folder).descriptor);
                    case CONTRIBUTED_BY:
                        ExtensionFolder ef = (ExtensionFolder)folder;
                        List<Object> children = new ArrayList<Object>();
                        children.add(ef.extension.getContributedBy());
                        children.addAll(super.getChildren(parent));
                        return children;
                    default:
                        break;
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
        public BundleRegistryQuery.Grouping getGroupBy()
        {
            return Grouping.BY_EXTENSION_POINT;
        }

        @Override
        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.BundleRegistryQuery_ExtensionPoints),//
                            new Column(Messages.BundleRegistryQuery_BundleState).noTotals() };
        }

    }

}
