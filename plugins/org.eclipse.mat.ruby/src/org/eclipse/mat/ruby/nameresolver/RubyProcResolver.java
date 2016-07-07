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
package org.eclipse.mat.ruby.nameresolver;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class RubyProcResolver
{

    @Subject("Proc")
    public static class ProcResolver implements IClassSpecificNameResolver
    {

        @Override
        public String resolve(IObject object) throws SnapshotException
        {
            ISnapshot snapshot = object.getSnapshot();
            int objectId = object.getObjectId();
            int[] outboundReferentIds = snapshot.getOutboundReferentIds(objectId);
            for (int i : outboundReferentIds)
            {
                IClass clazz = snapshot.getClassOf(i);
                if (clazz != null && clazz.getName().equals("RubyVM::Env"))
                {
                    int[] rubyEnvRefs = snapshot.getOutboundReferentIds(i);
                    for (int j : rubyEnvRefs)
                    {
                        IClass rubyEnvRefClass = snapshot.getClassOf(j);
                        if (rubyEnvRefClass != null && rubyEnvRefClass.getName().equals("RubyVM::InstructionSequence")) { return snapshot
                                        .getObject(j).getClassSpecificName(); }
                    }

                }
            }

            return null;
        }
    }

    @Subject("RubyVM::InstructionSequence")
    public static class RubyVM_InstructionSequenceResolver implements IClassSpecificNameResolver
    {

        @Override
        public String resolve(IObject object) throws SnapshotException
        {
            // FIXME: pure guessing here -
            /* just take the two shortest referenced strings assuming the shortest is the
             * method and the next one is the ruby file
             * works astonishingly well ;-) 
             */
            Map<Integer, String> stringsByLength = new TreeMap<>();
            ISnapshot snapshot = object.getSnapshot();
            int objectId = object.getObjectId();
            int[] outboundReferentIds = snapshot.getOutboundReferentIds(objectId);
            for (int i : outboundReferentIds)
            {
                IClass clazz = snapshot.getClassOf(i);
                if (clazz != null && clazz.getName().equals("String"))
                {
                    String value = snapshot.getObject(i).getClassSpecificName();
                    stringsByLength.put(value.length(), value);
                }
            }

            StringBuilder result = new StringBuilder();
            Set<Entry<Integer, String>> entrySet = stringsByLength.entrySet();
            Iterator<Entry<Integer, String>> iterator = entrySet.iterator();
            
            result.append(iterator.next().getValue()).append(" in ").append(iterator.next().getValue());
           
            return result.toString();
        }

    }

}
