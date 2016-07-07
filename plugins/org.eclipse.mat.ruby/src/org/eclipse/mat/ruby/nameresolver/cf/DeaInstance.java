/*******************************************************************************
 * Copyright (c) 2016 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.ruby.nameresolver.cf;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

@Subject("Dea::Instance")
public class DeaInstance implements IClassSpecificNameResolver
{
    
    static Map<String, SetInt> dump2Strings = new HashMap<>();

    public DeaInstance()
    {
        // TODO Auto-generated constructor stub
    }

    @Override
    public String resolve(IObject object) throws SnapshotException
    {
        
        String path = object.getSnapshot().getSnapshotInfo().getPath();
        SetInt registeredDeaInstances = dump2Strings.get(path);
        if (registeredDeaInstances == null)
        {
                registeredDeaInstances = readStringValues(object.getSnapshot());
                dump2Strings.put(path, registeredDeaInstances);
        }

        if (registeredDeaInstances != null) { 
            return registeredDeaInstances.contains(object.getObjectId()) ? "registered" : "UNREGISTERED";
        }

        return null;
    }

    private SetInt readStringValues(ISnapshot snapshot) throws SnapshotException
    {
        SetInt result = new SetInt();
        Collection<IClass> instanceRegistryClasses = snapshot.getClassesByName("Dea::InstanceRegistry", false);
        for (IClass clazz : instanceRegistryClasses)
        {
            int[] instanceRegistryInstances = clazz.getObjectIds();
            for (int instanceRegistry : instanceRegistryInstances)
            {
                int[] instanceRegistryRefs = snapshot.getOutboundReferentIds(instanceRegistry);
                for (int ref : instanceRegistryRefs)
                {
                    if (snapshot.getClassOf(ref).getName().equals("Hash")) {
                        int[] refsOfHash = snapshot.getOutboundReferentIds(ref);
                        for (int hashValue : refsOfHash)
                        {
                            if (snapshot.getClassOf(hashValue).getName().equals("Dea::Instance")) {
                                result.add(hashValue);
                            }
                        }
                    }
                }
                
            }
            
        }

        return result;
    }

}
