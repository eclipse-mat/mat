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
package org.eclipse.mat.parser.internal.oql;

import java.util.List;

import org.eclipse.mat.parser.internal.oql.compiler.Expression;
import org.eclipse.mat.parser.internal.oql.compiler.Query;

public interface ICompiler
{
    Object and(Object arguments[]);

    Object or(Object arguments[]);

    Object equal(Object left, Object right);

    Object notEqual(Object left, Object right);

    Object lessThan(Object left, Object right);

    Object lessThanOrEqual(Object left, Object right);

    Object greaterThan(Object left, Object right);

    Object greaterThanOrEqual(Object left, Object right);

    Object like(Object ex, String regex);

    Object notLike(Object ex, String regex);

    Object instanceOf(Object left, String className);

    Object in(Object left, Object right);

    Object notIn(Object left, Object right);

    Object literal(Object object);

    Object nullLiteral();

    Object path(List<Object> attributes);

    Object method(String name, List<Expression> parameters, boolean isFirstInPath);

    Object subQuery(Query q);

    Object plus(Object left, Object right);

    Object minus(Object left, Object right);

    Object multiply(Object left, Object right);

    Object divide(Object left, Object right);

}
