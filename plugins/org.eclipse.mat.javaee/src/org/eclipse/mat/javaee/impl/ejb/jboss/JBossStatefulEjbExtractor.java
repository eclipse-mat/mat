/*******************************************************************************
 * Copyright (c) 2014 Red Hat Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat Inc - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.javaee.impl.ejb.jboss;

import static org.eclipse.mat.javaee.Utils.findStaticObjectField;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.ejb.api.StatefulEjbExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class JBossStatefulEjbExtractor extends JBossEjbExtractorBase implements StatefulEjbExtractor {
    public Integer getInstanceCount(IObject component) {
        try {
            IObject instanceMap = resolveInstanceMap(component);
            if (instanceMap == null)
                return null;

            return CollectionExtractionUtils.extractMap(instanceMap).size();
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    public Map<IObject,IObject> getInstances(IObject component) {
        try {
            IObject instanceMap = resolveInstanceMap(component);
            if (instanceMap == null)
                return null;

            ExtractedMap extractedMap = CollectionExtractionUtils.extractMap(instanceMap);
            Integer size = extractedMap.size();
            Map<IObject,IObject> instances;
            if (size != null)
                instances = new HashMap<IObject,IObject>(size);
            else
                instances = new HashMap<IObject,IObject>();

            for (Map.Entry<IObject,IObject> entry: extractedMap) {
                IObject value = entry.getValue();
                final IObject instance;
                if (value.getClazz().getName().equals("org.jboss.as.ejb3.cache.impl.backing.NonPassivatingBackingCacheEntry")) {
                    instance = getAS7Instance(value);
                } else if (value.getClazz().getName().equals("org.jboss.as.ejb3.cache.simple.SimpleCache$Entry")) {
                    instance = getWildfly8Instance(value);
                } else {
                    JavaEEPlugin.warning("Unexpected cahce entry class: " + value.getClazz().getName());
                    continue;
                }

                if (instance != null)
                    instances.put(entry.getKey(), instance);
            }
            return instances;
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    private IObject resolveInstanceMap(IObject component) {
        try {
            IObject cache = (IObject)component.resolveValue("cache");
            String cacheClassName = cache.getClazz().getName();
            if (cacheClassName.equals("org.jboss.as.ejb3.cache.impl.SimpleCache")) {
                return resolveAS7InstanceMap(cache);
            } else if (cacheClassName.equals("org.jboss.as.ejb3.cache.simple.SimpleCache")) {
                return resolveWildfly8InstanceMap(cache);
            } else {
                JavaEEPlugin.error("Unexpected cache class: " + cache.getClazz().getName());
                return null;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    private IObject resolveAS7InstanceMap(IObject cache) throws SnapshotException {
        IObject backingCache = (IObject)cache.resolveValue("backingCache");
        if (!backingCache.getClazz().getName().equals("org.jboss.as.ejb3.cache.impl.backing.NonPassivatingBackingCacheImpl")) {
            JavaEEPlugin.error("Unexpected backing cache class: " + backingCache.getClazz().getName());
            return null;
        }

        return (IObject)backingCache.resolveValue("cache");
    }

    private IObject getAS7Instance(IObject value) throws SnapshotException {
        IObject componentInstance = (IObject)value.resolveValue( "wrapped");
        if (!componentInstance.getClazz().getName().equals("org.jboss.as.ejb3.component.stateful.StatefulSessionComponentInstance")) {
            JavaEEPlugin.warning("Unexpected wrapper class: " + componentInstance.getClazz().getName());
            return null;
        }

        IObject data = (IObject)componentInstance.resolveValue("instanceData");
        if (data != null) {
            //EAP 6
            IObject classBasicComponentInstanceKey = findStaticObjectField(componentInstance.getClazz(), "INSTANCE_KEY");
            return extractWildflyManagedReference(CollectionExtractionUtils.extractMap(data).getByKeyIdentity(classBasicComponentInstanceKey));
        } else {
            return (IObject)componentInstance.resolveValue("instanceReference.value.instance");
        }
    }

    private IObject resolveWildfly8InstanceMap(IObject cache) throws SnapshotException {
        return (IObject)cache.resolveValue("entries");
    }

    private IObject getWildfly8Instance(IObject value) throws SnapshotException {
        IObject componentInstance = (IObject)value.resolveValue("value");
        if (!componentInstance.getClazz().getName().equals("org.jboss.as.ejb3.component.stateful.StatefulSessionComponentInstance")) {
            JavaEEPlugin.warning("Unexpected wrapper class: " + componentInstance.getClazz().getName());
            return null;
        }
        IObject data = (IObject)componentInstance.resolveValue("instanceData");
        IObject classBasicComponentInstanceKey = findStaticObjectField(componentInstance.getClazz(), "INSTANCE_KEY");
        return extractWildflyManagedReference(CollectionExtractionUtils.extractMap(data).getByKeyIdentity(classBasicComponentInstanceKey));
    }

    private IObject extractWildflyManagedReference(IObject ref) throws SnapshotException {
        String className = ref.getClazz().getName();
        if (className.equals("org.jboss.as.weld.injection.WeldManagedReferenceFactory$WeldManagedReference")) {
            return (IObject)ref.resolveValue("instance");
        } else if (className.equals("org.jboss.as.naming.ValueManagedReferenceFactory$ValueManagedReference")) {
            return (IObject)ref.resolveValue("instance");
        } else if (className.equals("org.jboss.as.ejb3.timerservice.TimerServiceBindingSource$TimerServiceManagedReference")) {
            // this requires invoking code at runtime, can't do it.
            JavaEEPlugin.info("Cannot extract from TimerServiceManagedReference");
            return null;
        } else {
            JavaEEPlugin.warning("Unexpected reference class" + className);
            return null;
        }
    }
}
