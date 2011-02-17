/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.queries;

import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.util.IProgressListener;

/**
 * Test different context arguments to see which menus they appear in
 * @author ajohnson
 *
 */
public class Context
{

    @Category("Test")
    public static class ContextObjectQuery implements IQuery
    {
        @Argument
        public IContextObject a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Object id " + a.getObjectId());
            return ret;
        }
    }

    @Category("Test")
    public static class ContextObjectQueries implements IQuery
    {
        @Argument
        public IContextObject as[];
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            for (IContextObject a : as)
            {
                sb.append("Object id "+a.getObjectId()).append("\n");
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

    @Category("Test")
    public static class ContextObjectSetQuery implements IQuery
    {
        @Argument
        public IContextObjectSet a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Object id " + a.getObjectId()+" "+a.getOQL());
            return ret;
        }
    }

    @Category("Test")
    public static class ContextObjectSetQueries implements IQuery
    {
        @Argument
        public IContextObjectSet as[];
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            for (IContextObjectSet a : as)
            {
                sb.append("Object id " + a.getObjectId()+" "+a.getOQL()+"\n");
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

}
