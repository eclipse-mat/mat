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

import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.javaee.JavaEEPlugin;
import org.eclipse.mat.javaee.ejb.api.StatelessEjbExtractor;
import org.eclipse.mat.snapshot.model.IObject;

public class JBossStatelessEjbExtractor extends JBossEjbExtractorBase implements StatelessEjbExtractor {
    public Integer getInstanceCount(IObject component) {
        try {
            IObject pool = (IObject)component.resolveValue("pool");
            String poolClass = pool.getClazz().getName();
            if (poolClass.equals("org.jboss.as.ejb3.pool.strictmax.StrictMaxPool")) {
                return countStrictMaxPool(pool);
            } else {
                JavaEEPlugin.error("unhandled pool class");
                return null;
            }
        } catch (SnapshotException e) {
            JavaEEPlugin.error(e);
            return null;
        }
    }

    private Integer countStrictMaxPool(IObject pool) throws SnapshotException {
        Integer state = (Integer) pool.resolveValue("semaphore.sync.state");
        Integer maxSize = (Integer) pool.resolveValue("maxSize");

        if (state != null && maxSize != null) {
            return maxSize - state;
        } else {
            return null;
        }
    }

    public Map<IObject,IObject> getInstances(IObject component) {
       throw new IllegalStateException("not implemented");
    }
}
