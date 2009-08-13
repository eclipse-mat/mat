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
package org.eclipse.mat.inspections.osgi.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.osgi.model.BundleDescriptor.Type;
import org.eclipse.mat.inspections.osgi.model.eclipse.ConfigurationElement;
import org.eclipse.mat.inspections.osgi.model.eclipse.Extension;
import org.eclipse.mat.inspections.osgi.model.eclipse.ExtensionPoint;
import org.eclipse.mat.internal.MATPlugin;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

public class EquinoxBundleReader implements IBundleReader
{
    private Map<String, Bundle> bundles = new HashMap<String, Bundle>();
    private ISnapshot snapshot;
    private Map<BundleDescriptor, List<Service>> registeredServices = new HashMap<BundleDescriptor, List<Service>>();
    private Map<BundleDescriptor, List<Service>> usedServices = new HashMap<BundleDescriptor, List<Service>>();
    private Map<Long, BundleDescriptor> bundleDescriptors = new HashMap<Long, BundleDescriptor>();
    // bundle name -> ExtensionPoints
    private Map<BundleDescriptor, List<ExtensionPoint>> extensionPointsByBundle = new HashMap<BundleDescriptor, List<ExtensionPoint>>();
    // bundle name -> Extensions
    private Map<BundleDescriptor, List<Extension>> extensionsByBundle = new HashMap<BundleDescriptor, List<Extension>>();

    public EquinoxBundleReader(ISnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    private enum BundleState
    {
        ACTIVE(Messages.EquinoxBundleReader_State_Active, 32), // 
        INSTALLED(Messages.EquinoxBundleReader_State_Installed, 2), //
        RESOLVED(Messages.EquinoxBundleReader_State_Resolved, 4), //
        STARTING(Messages.EquinoxBundleReader_State_Starting, 8), //
        STOPPING(Messages.EquinoxBundleReader_State_Stopping, 16), //
        UNINSTALLED(Messages.EquinoxBundleReader_State_Uninstalled, 1);//

        private String label;
        private int value;

        private BundleState(String label, int value)
        {
            this.label = label;
            this.value = value;
        }

        public String getLabel()
        {
            return label;
        }

        public int getValue()
        {
            return value;
        }
    }

    public OSGiModel readOSGiModel(IProgressListener listener) throws SnapshotException
    {
        List<BundleDescriptor> descriptors = getBundleDescriptors(listener);
        List<Service> services = collectServiceInfo(listener);
        List<ExtensionPoint> extensionPoints = collectExtensionsInfo(listener);

        OSGiModel model = new OSGiModel(this, descriptors, services, extensionPoints);
        return model;

    }

    private List<BundleDescriptor> getBundleDescriptors(IProgressListener listener) throws SnapshotException
    {
        listener.subTask(Messages.EquinoxBundleReader_ReadingBundles);
        Collection<IClass> classes = snapshot.getClassesByName(
                        "org.eclipse.osgi.framework.internal.core.BundleRepository", false); //$NON-NLS-1$
        List<BundleDescriptor> bundleDescriptors = new ArrayList<BundleDescriptor>();
        if (classes == null || classes.isEmpty())
            return bundleDescriptors;

        for (IClass clazz : classes)
        {
            int[] objs = clazz.getObjectIds();

            for (int i = 0; i < objs.length; i++)
            {
                IInstance obj = (IInstance) snapshot.getObject(objs[i]);

                IObjectArray bundlesArray = (IObjectArray) obj.resolveValue("bundlesByInstallOrder.elementData");//$NON-NLS-1$
                if (bundlesArray == null)
                    return null;
                long[] bundleAddreses = bundlesArray.getReferenceArray();
                if (bundleAddreses != null)
                {
                    for (long address : bundleAddreses)
                    {
                        if (address == 0)
                            continue;

                        if (listener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();

                        int objectId = snapshot.mapAddressToId(address);
                        IObject bundleObject = snapshot.getObject(objectId);
                        BundleDescriptor.Type type = BundleDescriptor.Type.BUNDLE;
                        if (bundleObject.getClazz().getName().equals(
                                        "org.eclipse.osgi.framework.internal.core.BundleFragment"))//$NON-NLS-1$
                        {
                            type = BundleDescriptor.Type.FRAGMENT;
                        }
                        BundleDescriptor descriptor = getBundleDescriptor(bundleObject, type);
                        bundleDescriptors.add(descriptor);
                    }
                }
            }
        }
        return bundleDescriptors;
    }

    private List<BundleDescriptor> getBundleFragments(IObject bundleHostObject) throws SnapshotException
    {
        IObject fragmentObject = (IObject) bundleHostObject.resolveValue("fragments");//$NON-NLS-1$
        if (fragmentObject == null)
            return null;

        if (fragmentObject.getClazz().isArrayType())
        {
            long[] addresses = ((IObjectArray) fragmentObject).getReferenceArray();
            List<BundleDescriptor> fragments = new ArrayList<BundleDescriptor>(addresses.length);
            for (int i = 0; i < addresses.length; i++)
            {
                int id = snapshot.mapAddressToId(addresses[i]);
                IObject obj = snapshot.getObject(id);
                BundleDescriptor descriptor = getBundleDescriptor(obj, Type.FRAGMENT);
                fragments.add(descriptor);

            }
            return fragments;
        }
        else
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedArrayType, Long
                            .toHexString(fragmentObject.getObjectAddress())));
            return null;
        }

    }

    private List<Service> collectServiceInfo(IProgressListener listener) throws SnapshotException
    {
        listener.subTask(Messages.EquinoxBundleReader_ReadingServices);

        Collection<IClass> classes = snapshot.getClassesByName( // 3.5
                        "org.eclipse.osgi.internal.serviceregistry.ServiceRegistry", false); //$NON-NLS-1$
        if (classes == null || classes.isEmpty())
            classes = snapshot.getClassesByName( // 3.4
                            "org.eclipse.osgi.framework.internal.core.ServiceRegistryImpl", false); //$NON-NLS-1$

        List<Service> services = new ArrayList<Service>();
        if (classes == null || classes.isEmpty())
            return services;

        for (IClass clazz : classes)
        {
            int[] objs = clazz.getObjectIds();

            for (int i = 0; i < objs.length; i++)
            {
                IInstance obj = (IInstance) snapshot.getObject(objs[i]);

                IObjectArray servicesArray = (IObjectArray) obj.resolveValue("allPublishedServices.elementData");//$NON-NLS-1$
                if (servicesArray == null)
                    continue; // Try with other registries
                long[] serviceAddreses = servicesArray.getReferenceArray();
                if (serviceAddreses == null)
                    continue;
                for (long address : serviceAddreses)
                {
                    if (address == 0)
                        continue;
                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();
                    int serviceInstanceId = snapshot.mapAddressToId(address);
                    IInstance serviceInstance = (IInstance) snapshot.getObject(serviceInstanceId);
                    IObject bundleObj = (IObject) serviceInstance.resolveValue("bundle"); //$NON-NLS-1$
                    BundleDescriptor bundleDescriptor = getBundleDescriptor(bundleObj, Type.BUNDLE);

                    List<BundleDescriptor> bundlesUsing = null;
                    IObjectArray bundlesArray = (IObjectArray) serviceInstance
                                    .resolveValue("contextsUsing.elementData");//$NON-NLS-1$
                    if (bundlesArray != null)
                    {
                        long[] bundleAddresses = bundlesArray.getReferenceArray();
                        if (bundleAddresses != null)
                        {
                            bundlesUsing = new ArrayList<BundleDescriptor>(bundleAddresses.length);
                            for (long bundleAddress : bundleAddresses)
                            {
                                int bundleId = snapshot.mapAddressToId(bundleAddress);
                                IInstance bundleInstance = (IInstance) snapshot.getObject(bundleId);
                                IObject bundleObject = (IObject) bundleInstance.resolveValue("bundle");//$NON-NLS-1$
                                if (bundleObject == null)
                                    continue;

                                BundleDescriptor usingBundleDescriptor = getBundleDescriptor(bundleObject, Type.BUNDLE);
                                bundlesUsing.add(usingBundleDescriptor);
                            }
                        }
                    }
                    // get service name
                    IObjectArray clazzes = (IObjectArray) serviceInstance.resolveValue("clazzes");//$NON-NLS-1$
                    String serviceName = null;
                    if (clazzes != null)
                    {
                        long[] serviceNameArray = clazzes.getReferenceArray();
                        for (long l : serviceNameArray)
                        {
                            try
                            {
                                int id = snapshot.mapAddressToId(l);
                                IInstance instance = (IInstance) snapshot.getObject(id);
                                serviceName = instance.getClassSpecificName();
                                break; // only one element
                            }
                            catch (SnapshotException e)
                            {
                                MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ServiceName,
                                                Long.toHexString(l)));
                            }
                        }
                    }
                    // get properties
                    IObject propertiesObject = (IObject) serviceInstance.resolveValue("properties");//$NON-NLS-1$
                    String[] keys = null;
                    String[] values = null;
                    if (propertiesObject != null)
                    {
                        IObjectArray keysArray = (IObjectArray) propertiesObject.resolveValue("headers"); //$NON-NLS-1$
                        if (keysArray != null)
                        {
                            long[] keyAddresses = keysArray.getReferenceArray();
                            if (keyAddresses != null)
                            {
                                keys = getServiceProperties(new String[keyAddresses.length], keyAddresses);
                            }
                        }
                        IObjectArray valuesArray = (IObjectArray) propertiesObject.resolveValue("values"); //$NON-NLS-1$
                        if (valuesArray != null)
                        {
                            long[] valueAddresses = valuesArray.getReferenceArray();
                            if (valueAddresses != null)
                            {
                                values = getServiceProperties(new String[valueAddresses.length], valueAddresses);
                            }
                        }
                    }

                    services.add(new Service(serviceName, serviceInstanceId, bundleDescriptor, bundlesUsing, keys,
                                    values));
                }
            }
        }

        if (services.size() > 0)
            updateServiceMap(services);
        return services;
    }

    private String[] getServiceProperties(String[] values, long[] valueAddresses)
    {
        for (int j = 0; j < valueAddresses.length; j++)
        {
            try
            {
                int valueId = snapshot.mapAddressToId(valueAddresses[j]);
                IObject valueObject = snapshot.getObject(valueId);
                if (valueObject == null)
                    continue;
                if (valueObject.getClazz().isArrayType())
                {
                    long[] addresses = ((IObjectArray) valueObject).getReferenceArray();

                    for (int k = 0; k < addresses.length; k++)
                    {
                        int id = snapshot.mapAddressToId(addresses[k]);
                        IObject object = snapshot.getObject(id);
                        if (object == null)
                            continue;
                        values[j] = object.getClassSpecificName();
                        break; // is more than one element possible in that
                        // array?
                    }
                }
                else
                {
                    values[j] = valueObject.getClassSpecificName();
                }
            }
            catch (SnapshotException e)
            {
                values[j] = null;
                MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ServiceProperty, Long
                                .toHexString(valueAddresses[j])));
            }
        }
        return values;
    }

    private void updateServiceMap(List<Service> services)
    {
        for (Service service : services)
        {
            BundleDescriptor contributedBy = service.getBundleDescriptor();
            // update registered services
            doUpdate(service, contributedBy, registeredServices);

            // update used services
            List<BundleDescriptor> bundlesUsing = service.getBundlesUsing();
            if (bundlesUsing != null)
                for (BundleDescriptor descriptor : bundlesUsing)
                {
                    doUpdate(service, descriptor, usedServices);
                }
        }

    }

    private void doUpdate(Service service, BundleDescriptor bundleDescriptor,
                    Map<BundleDescriptor, List<Service>> serviceMap)
    {
        List<Service> listOfServices = serviceMap.get(bundleDescriptor);
        if (listOfServices == null)
        {
            List<Service> bundleServices = new ArrayList<Service>(1);
            bundleServices.add(service);
            serviceMap.put(bundleDescriptor, bundleServices);
        }
        else if (!listOfServices.contains(service))
        {
            listOfServices.add(service);
        }
    }

    private BundleDescriptor getBundleDescriptor(IObject bundleHostObject, BundleDescriptor.Type type)
                    throws SnapshotException
    {
        IInstance bundleData = (IInstance) bundleHostObject.resolveValue("bundledata");//$NON-NLS-1$
        Field idField = bundleData.getField("id"); //$NON-NLS-1$
        Long id = null;
        if (idField != null)
        {
            id = (Long) idField.getValue();
        }
        // check whether this bundle descriptor is already in the map
        BundleDescriptor bundleDescriptor = bundleDescriptors.get(id);
        if (bundleDescriptor != null)
            return bundleDescriptor;

        String bundleName = extractBundleName(bundleData);
        String state = getState(bundleHostObject);

        BundleDescriptor descriptor = new BundleDescriptor(bundleHostObject.getObjectId(), id, bundleName, state, type);
        // add new bundle name to the map. Key is bundleId
        bundleDescriptors.put(id, descriptor);

        return descriptor;
    }

    private String extractBundleName(IObject bundleData) throws SnapshotException
    {
        IObject name = (IObject) bundleData.resolveValue("symbolicName");//$NON-NLS-1$
        String symbolicName = name.getClassSpecificName();

        IObject versionObj = (IObject) bundleData.resolveValue("version.qualifier.value");//$NON-NLS-1$
        String version = null;
        if (versionObj != null)
        {
            version = versionObj.getClassSpecificName();
        }
        String bundleName = version == null || version.equals("") ? symbolicName : symbolicName + " (" + version + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return bundleName;
    }

    public Bundle getBundle(BundleDescriptor descriptor) throws SnapshotException
    {
        Bundle bundle = bundles.get(descriptor.getBundleName());
        if (bundle == null)
            bundle = load(descriptor);
        return bundle;

    }

    private Bundle load(BundleDescriptor descriptor) throws SnapshotException
    {
        int objectId = descriptor.getObjectId();
        IInstance obj = (IInstance) snapshot.getObject(objectId);

        IObject locationObj = (IObject) obj.resolveValue("bundledata.fileName");//$NON-NLS-1$
        String location = null;
        if (locationObj != null)
        {
            location = locationObj.getClassSpecificName();
        }
        if (descriptor.getType().equals(BundleDescriptor.Type.FRAGMENT))
        {
            BundleDescriptor host = getFragmentHost(obj);
            return new BundleFragment(descriptor, location, host);
        }
        List<BundleDescriptor> dependencies = null;
        List<BundleDescriptor> dependents = null;

        IObject bundleDescriptionObject = (IObject) obj.resolveValue("bundledata.bundle.proxy.description");//$NON-NLS-1$
        if (bundleDescriptionObject != null)
        {
            IObjectArray resolvedValue = (IObjectArray) bundleDescriptionObject
                            .resolveValue("dependencies.elementData");//$NON-NLS-1$
            dependencies = getDependencies(snapshot, resolvedValue);

            IObjectArray resolvedDependentsValue = (IObjectArray) bundleDescriptionObject
                            .resolveValue("dependents.elementData");//$NON-NLS-1$
            dependents = getDependencies(snapshot, resolvedDependentsValue);
        }

        List<Extension> extensions = extensionsByBundle.get(descriptor);
        List<ExtensionPoint> extensionPoints = extensionPointsByBundle.get(descriptor);

        List<Service> registeredServices = this.registeredServices.get(descriptor);
        List<Service> usedServices = this.usedServices.get(descriptor);

        List<BundleDescriptor> fragments = getBundleFragments(obj);

        return new Bundle(descriptor, location, dependencies, dependents, extensionPoints, extensions,
                        registeredServices, usedServices, fragments);

    }

    private BundleDescriptor getFragmentHost(IObject bundleFragmentObject) throws SnapshotException
    {
        IObject fragmentObject = (IObject) bundleFragmentObject.resolveValue("hosts");//$NON-NLS-1$
        if (fragmentObject == null)
            return null;

        if (!fragmentObject.getClazz().isArrayType())
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedArrayType, Long
                            .toHexString(fragmentObject.getObjectAddress())));
            return null;
        }

        IObjectArray hosts = (IObjectArray) fragmentObject;
        if (hosts.getLength() == 0)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_BundleNotFound, Long
                            .toHexString(fragmentObject.getObjectAddress())));
            return null;
        }

        long[] addresses = hosts.getReferenceArray();
        int hostId = snapshot.mapAddressToId(addresses[0]);
        IObject bundleObject = null;

        if ("org.eclipse.osgi.framework.internal.core.BundleHost[]".equals( //$NON-NLS-1$
                        fragmentObject.getClazz().getName()))
        {
            // 3.5
            bundleObject = snapshot.getObject(hostId);
        }
        else if ("org.eclipse.osgi.framework.internal.core.BundleLoaderProxy[]".equals( //$NON-NLS-1$
                        fragmentObject.getClazz().getName()))
        {
            // 3.4
            IObject bundleLoaderObject = snapshot.getObject(hostId);
            bundleObject = (IObject) bundleLoaderObject.resolveValue("bundle");//$NON-NLS-1$
        }

        if (bundleObject == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_BundleNotFound, Long
                            .toHexString(fragmentObject.getObjectAddress())));
            return null;
        }
        else
        {
            return getBundleDescriptor(bundleObject, Type.BUNDLE);
        }
    }

    private String getState(IObject obj) throws SnapshotException
    {
        IInstance bundleHostObject = (IInstance) obj.resolveValue("bundledata.bundle");//$NON-NLS-1$
        Field stateField = bundleHostObject.getField("state");//$NON-NLS-1$
        if (stateField == null)
            return Messages.EquinoxBundleReader_NotApplicable;

        int state = ((Integer) stateField.getValue()).intValue();

        for (BundleState stateType : BundleState.values())
        {
            if (stateType.getValue() == state)
                return stateType.getLabel();
        }

        return Messages.EquinoxBundleReader_NotApplicable;
    }

    private List<BundleDescriptor> getDependencies(ISnapshot snapshot, IObjectArray resolvedValue)
                    throws SnapshotException
    {
        List<BundleDescriptor> dependencyDescriptors = null;
        if (resolvedValue != null)
        {
            long[] dependencyAddreses = resolvedValue.getReferenceArray();
            if (dependencyAddreses != null)
            {
                dependencyDescriptors = new ArrayList<BundleDescriptor>(dependencyAddreses.length);
                for (long address : dependencyAddreses)
                {
                    int objectId = snapshot.mapAddressToId(address);
                    IObject bundleDescriptionObject = snapshot.getObject(objectId);
                    IObject bundleHostObject = (IObject) bundleDescriptionObject.resolveValue("userObject.bundle");//$NON-NLS-1$
                    if (bundleHostObject == null)
                        continue;

                    BundleDescriptor.Type type = BundleDescriptor.Type.BUNDLE;
                    if (bundleHostObject.getClazz().getName().equals(
                                    "org.eclipse.osgi.framework.internal.core.BundleFragment"))//$NON-NLS-1$
                        type = BundleDescriptor.Type.FRAGMENT;

                    BundleDescriptor descriptor = getBundleDescriptor(bundleHostObject, type);
                    dependencyDescriptors.add(descriptor);
                }
            }
        }
        return dependencyDescriptors;
    }

    private List<ExtensionPoint> collectExtensionsInfo(IProgressListener listener) throws SnapshotException
    {
        listener.subTask(Messages.EquinoxBundleReader_ReadingExtensions);
        Collection<IClass> classes = snapshot.getClassesByName(
                        "org.eclipse.core.internal.registry.ExtensionRegistry", false); //$NON-NLS-1$

        if (classes == null || classes.isEmpty())
            return null;
        Map<String, ExtensionPoint> extensionPoints = new HashMap<String, ExtensionPoint>();
        Map<Integer, Extension> extensions = new HashMap<Integer, Extension>();
        Map<Integer, ConfigurationElement> configElements = new HashMap<Integer, ConfigurationElement>();

        for (IClass clazz : classes)
        {
            int[] objs = clazz.getObjectIds();

            for (int i = 0; i < objs.length; i++)
            {
                IInstance obj = (IInstance) snapshot.getObject(objs[i]);

                IObjectArray heldObjectsArray = (IObjectArray) obj.resolveValue("registryObjects.heldObjects.elements");//$NON-NLS-1$
                if (heldObjectsArray == null)
                    continue;
                long[] heldObjectAddreses = heldObjectsArray.getReferenceArray();
                if (heldObjectAddreses != null)
                {
                    for (long address : heldObjectAddreses)
                    {
                        if (address == 0)
                            continue;

                        if (listener.isCanceled())
                            throw new IProgressListener.OperationCanceledException();

                        int objectId = snapshot.mapAddressToId(address);
                        IObject instance = snapshot.getObject(objectId);

                        // get type of object (Extension, ExtensionPoint,
                        // ConfigurationElement)
                        String className = instance.getClazz().getName();
                        if (className.equals("org.eclipse.core.internal.registry.ExtensionPoint")) //$NON-NLS-1$
                        {
                            ExtensionPoint extensionPoint = extractExtensionPointInfo(instance);
                            if (extensionPoint != null && !extensionPoints.containsValue(extensionPoint))
                                extensionPoints.put(extensionPoint.getName(), extensionPoint);
                            else if (extensionPoint != null)
                                MATPlugin.log(MessageUtil.format(
                                                Messages.EquinoxBundleReader_ErrorMsg_DuplicateExtensionPoint,
                                                extensionPoint.getName()));
                        }
                        else if (className.equals("org.eclipse.core.internal.registry.ConfigurationElement")) //$NON-NLS-1$
                        {
                            ConfigurationElement configElement = extractConfigurationElementInfo(instance);
                            if (configElement != null && !configElements.containsValue(configElement))
                                configElements.put(configElement.getElementId(), configElement);
                            else if (configElement != null)
                                MATPlugin.log(MessageUtil.format(
                                                Messages.EquinoxBundleReader_ErrorMsg_DuplicateConfigurationElement,
                                                configElement.getName(), configElement.getObjectId()));
                        }
                        else if (className.equals("org.eclipse.core.internal.registry.Extension")) //$NON-NLS-1$
                        {
                            Extension extension = extractExtensionInfo(instance);
                            if (extension != null && !extensions.containsValue(extension))
                                extensions.put(extension.getExtensionId(), extension);
                            else if (extension != null)
                                MATPlugin.log(MessageUtil.format(
                                                Messages.EquinoxBundleReader_ErrorMsg_DuplicateExtension, extension
                                                                .getName()));
                        }
                        else
                        {
                            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_UnknownElementType,
                                            Long.toHexString(address)));
                            continue;
                        }

                    }
                }
            }
        }
        // add ConfigurationElements to corresponding Extensions or
        // ConfigurationElements
        Set<Entry<Integer, ConfigurationElement>> configElementSet = configElements.entrySet();
        for (Entry<Integer, ConfigurationElement> entry : configElementSet)
        {
            ConfigurationElement element = entry.getValue();
            Extension extension = extensions.get(element.getParentId());
            if (extension == null)
            {
                ConfigurationElement configElement = configElements.get(element.getParentId());
                if (configElement == null)
                    continue;
                configElement.addConfigurationElement(element);
                continue;
            }
            extension.addConfigurationElement(element);
        }

        // add Extensions to corresponding ExtensionPoints
        Set<Entry<Integer, Extension>> set = extensions.entrySet();
        for (Entry<Integer, Extension> entry : set)
        {
            Extension extension = entry.getValue();
            String name = extension.getName();
            ExtensionPoint extensionPoint = extensionPoints.get(name);
            if (extensionPoint == null)
                continue;
            extensionPoint.addExtension(extension);
        }

        // fill maps extensionsByBundle, ExtensionPointsByBundle
        Set<Entry<Integer, Extension>> extensionsSet = extensions.entrySet();
        for (Entry<Integer, Extension> entry : extensionsSet)
        {
            Extension extension = entry.getValue();
            BundleDescriptor bundleDescriptor = extension.getContributedBy();

            List<Extension> listOfExtensions = extensionsByBundle.get(bundleDescriptor);
            if (listOfExtensions == null)
            {
                List<Extension> extensionList = new ArrayList<Extension>(1);
                extensionList.add(extension);
                extensionsByBundle.put(bundleDescriptor, extensionList);
            }
            else if (!listOfExtensions.contains(extension))
            {
                listOfExtensions.add(extension);
            }
        }
        Set<Entry<String, ExtensionPoint>> pointsSet = extensionPoints.entrySet();
        for (Entry<String, ExtensionPoint> entry : pointsSet)
        {
            ExtensionPoint point = entry.getValue();
            BundleDescriptor bundleDescriptor = point.getContributedBy();
            List<ExtensionPoint> listOfExtensions = extensionPointsByBundle.get(bundleDescriptor);
            if (listOfExtensions == null)
            {
                List<ExtensionPoint> bundleExtensions = new ArrayList<ExtensionPoint>(1);
                bundleExtensions.add(point);
                extensionPointsByBundle.put(bundleDescriptor, bundleExtensions);
            }
            else if (!listOfExtensions.contains(point))
            {
                listOfExtensions.add(point);
            }
        }
        // return a list of extension points
        Set<Entry<BundleDescriptor, List<ExtensionPoint>>> extensionPointSet = extensionPointsByBundle.entrySet();
        List<ExtensionPoint> points = new ArrayList<ExtensionPoint>();
        for (Entry<BundleDescriptor, List<ExtensionPoint>> entry : extensionPointSet)
        {
            List<ExtensionPoint> list = entry.getValue();
            for (ExtensionPoint extensionPoint : list)
            {
                if (!points.contains(extensionPoint))
                    points.add(extensionPoint);
            }
        }
        return points;

    }

    private ConfigurationElement extractConfigurationElementInfo(IObject instance) throws SnapshotException
    {
        Field idField = ((IInstance) instance).getField("parentId"); //$NON-NLS-1$
        if (idField == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedFieldParent, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }

        Integer parentId = (Integer) idField.getValue();

        Field objectIdField = ((IInstance) instance).getField("objectId"); //$NON-NLS-1$
        if (objectIdField == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedFieldObjectId, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }

        Integer objectId = (Integer) objectIdField.getValue();

        IObject contributorObject = (IObject) instance.resolveValue("contributorId");//$NON-NLS-1$
        if (contributorObject == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ExpectedFieldContributorId, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }
        BundleDescriptor contributedBy = bundleDescriptors.get(Long.valueOf(contributorObject.getClassSpecificName()));

        IObject nameObject = (IObject) instance.resolveValue("name");//$NON-NLS-1$
        if (nameObject == null)
        {
            // some configuration elements contain only description. In that
            // case attribute name is not available.
            return null;
        }
        String name = nameObject.getClassSpecificName();
        IObject propertiesObject = (IObject) instance.resolveValue("propertiesAndValue");//$NON-NLS-1$
        if (propertiesObject == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ExpectedFieldPropertiesAndValues, Long
                            .toHexString(instance.getObjectAddress())));
        }
        else if (propertiesObject.getClazz().isArrayType())
        {
            long[] addresses = ((IObjectArray) propertiesObject).getReferenceArray();
            String[] propertiesAndValues = new String[addresses.length];
            for (int i = 0; i < addresses.length; i++)
            {
                try
                {
                    int id = snapshot.mapAddressToId(addresses[i]);

                    IObject object = snapshot.getObject(id);
                    propertiesAndValues[i] = object.getClassSpecificName();
                }
                catch (SnapshotException e)
                {
                    MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ReadingProperty, Long
                                    .toHexString(addresses[i])));
                    propertiesAndValues[i] = null;
                }

            }
            return new ConfigurationElement(instance.getObjectId(), name, parentId, objectId, contributedBy,
                            propertiesAndValues);
        }
        else
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedStringArray, Long
                            .toHexString(instance.getObjectAddress())));
        }
        return new ConfigurationElement(instance.getObjectId(), name, parentId, objectId, contributedBy, null);

    }

    private Extension extractExtensionInfo(IObject instance) throws SnapshotException
    {
        String[] properties = getExtensionProperties(instance);
        if (properties == null)
            return null;

        Field id = ((IInstance) instance).getField("objectId"); //$NON-NLS-1$
        if (id == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedFieldObjectId, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }
        Integer extensionId = (Integer) id.getValue();
        Extension extension = new Extension(instance.getObjectId(), extensionId, properties);
        BundleDescriptor contributedBy = bundleDescriptors.get(Long.valueOf(extension.getContributorId()));

        extension.setContributedBy(contributedBy);

        return extension;
    }

    private String[] getExtensionProperties(IObject instance) throws SnapshotException
    {
        IObject extraInfoObject = (IObject) instance.resolveValue("extraInformation");//$NON-NLS-1$
        if (extraInfoObject == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedFieldExtraInformation, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }

        if (extraInfoObject.getClazz().isArrayType())
        {
            long[] addresses = ((IObjectArray) extraInfoObject).getReferenceArray();
            String[] properties = new String[addresses.length];
            for (int i = 0; i < addresses.length; i++)
            {
                int id = snapshot.mapAddressToId(addresses[i]);
                IObject object = snapshot.getObject(id);
                properties[i] = object.getClassSpecificName();

            }
            return properties;
        }
        else
        {
            // TODO SoftReferences are not handled, as referents were always
            // null. Log, if otherwise.
            IObject referentObject = (IObject) extraInfoObject.resolveValue("referent"); //$NON-NLS-1$
            if (referentObject != null)
                MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_SoftReferencesNotHandled, Long
                                .toHexString(instance.getObjectAddress())));
            return null;
        }
    }

    private ExtensionPoint extractExtensionPointInfo(IObject instance) throws SnapshotException
    {
        String[] properties = getExtensionProperties(instance);
        if (properties == null)
            return null;

        Field id = ((IInstance) instance).getField("objectId"); //$NON-NLS-1$
        if (id == null)
        {
            MATPlugin.log(MessageUtil.format(Messages.EquinoxBundleReader_ErrorMsg_ExpectedFieldObjectId, Long
                            .toHexString(instance.getObjectAddress())));
            return null;
        }
        Integer extensionPointId = (Integer) id.getValue();
        ExtensionPoint extensionPoint = new ExtensionPoint(instance.getObjectId(), extensionPointId, properties);
        BundleDescriptor contributedBy = bundleDescriptors.get(Long.valueOf(extensionPoint.getContributorId()));
        extensionPoint.setContributedBy(contributedBy);

        return extensionPoint;
    }

}
