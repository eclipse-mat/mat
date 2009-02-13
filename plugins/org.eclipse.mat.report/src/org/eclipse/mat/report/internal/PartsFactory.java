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
package org.eclipse.mat.report.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.util.MessageUtil;

public class PartsFactory
{
    private int nextId = 1;
    private Map<Spec, AbstractPart> parts = new HashMap<Spec, AbstractPart>();

    public AbstractPart createRoot(Spec spec)
    {
        return build(null, spec);
    }

    public AbstractPart create(AbstractPart parent, Spec spec)
    {
        return build(parent, spec);
    }

    private AbstractPart build(AbstractPart parent, Spec spec)
    {
        AbstractPart p = parts.get(spec);
        if (p != null)
            return new LinkedPart(String.valueOf(nextId++), parent, p.dataFile, p.spec, p);

        DataFile artefact = new DataFile();
        AbstractPart answer = null;

        if (spec instanceof SectionSpec)
            answer = new SectionPart(String.valueOf(nextId++), parent, artefact, (SectionSpec) spec);
        else if (spec instanceof QuerySpec)
            answer = new QueryPart(String.valueOf(nextId++), parent, artefact, (QuerySpec) spec);

        if (answer == null)
            throw new RuntimeException(MessageUtil.format(Messages.PartsFactory_Error_Construction, spec.getClass()
                            .getName()));

        answer.init(this);

        parts.put(spec, answer);
        return answer;
    }

    public AbstractPart createClone(AbstractPart template, Spec spec)
    {
        DataFile artefact = template.getDataFile();
        AbstractPart answer = null;

        if (spec instanceof SectionSpec)
            answer = new SectionPart(template.getId(), template.getParent(), artefact, (SectionSpec) spec);
        else if (spec instanceof QuerySpec)
            answer = new QueryPart(template.getId(), template.getParent(), artefact, (QuerySpec) spec);

        if (answer == null)
            throw new RuntimeException(MessageUtil.format(Messages.PartsFactory_Error_Construction, spec.getClass()
                            .getName()));

        answer.objects = template.objects;

        // overwrite all parameters explicitly given (but not to the spec)
        answer.params = new Parameters.Deep(answer.params, template.params);

        answer.init(this);

        parts.put(spec, answer);

        return answer;
    }

}
