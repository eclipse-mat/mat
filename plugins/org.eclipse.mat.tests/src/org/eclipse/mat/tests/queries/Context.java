/*******************************************************************************
 * Copyright (c) 2011,2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Andrew Johnson - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.tests.queries;

import java.util.List;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

/**
 * Test different context arguments to see which menus they appear in
 * @author ajohnson
 *
 */
public class Context
{

    @Category("Test")
    @Help("Single context object")
    public static class ContextObjectQuery implements IQuery
    {
        @Argument
        public IContextObject a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Context object id " + a.getObjectId());
            return ret;
        }
    }

    @Category("Test")
    @Help("Optional single context object")
    public static class OptionalContextObjectQuery implements IQuery
    {
        @Argument(isMandatory = false)
        public IContextObject a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Context object id " + (a != null ? a.getObjectId() : "<omitted>"));
            return ret;
        }
    }

    @Category("Test")
    @Help("Optional single object")
    public static class OptionalObjectQuery implements IQuery
    {
        @Argument(isMandatory = false)
        public IObject a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Object id " + (a != null ? a.getObjectId() : "<omitted>"));
            return ret;
        }
    }

    @Category("Test")
    @Help("Multiple context objects")
    public static class ContextObjectsQuery implements IQuery
    {
        @Argument
        public IContextObject as[];
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            for (IContextObject a : as)
            {
                sb.append("Context object id "+a.getObjectId()).append("\n");
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

    @Category("Test")
    @Help("Single context set")
    public static class ContextObjectSetQuery implements IQuery
    {
        @Argument
        public IContextObjectSet a;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Context object id " + a.getObjectId()+" "+a.getOQL());
            return ret;
        }
    }

    @Category("Test")
    @Help("Multiple context sets")
    public static class ContextObjectSetsQuery implements IQuery
    {
        @Argument
        public IContextObjectSet as[];
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            for (IContextObjectSet a : as)
            {
                sb.append("Context object id " + a.getObjectId()+" "+a.getOQL()+"\n");
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }


    @Category("Test")
    @Help("Result table")
    public static class ResultTableQuery implements IQuery
    {
        
        @Argument
        public IResultTable result;
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            sb.append("Result table: row count: "+result.getRowCount()+"\n");
            for (Column c: result.getColumns()) {
                sb.append(c.getLabel());
                sb.append(' ');
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }
    
    @Category("Test")
    @Help("Result tree")
    public static class ResultTreeQuery implements IQuery
    {
        
        @Argument
        public IResultTree result;
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            sb.append("Result tree: top level count:"+result.getElements().size()+"\n");
            for (Column c: result.getColumns()) {
                sb.append(c.getLabel());
                sb.append(' ');
            }
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }
    
    @Category("Test")
    @Help("Structured result")
    public static class StructuredResultQuery implements IQuery
    {
        
        @Argument
        public IStructuredResult result;
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            sb.append("Structured result: "+result);
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

    @Category("Test")
    @Help("Structured results")
    public static class StructuredResultsQuery implements IQuery
    {
        
        @Argument
        public List<IStructuredResult> results;
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();;
            sb.append("Structured results: "+results);
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

    @Category("Test")
    @Help("Structured result and results")
    public static class StructuredResults2Query implements IQuery
    {
        
        @Argument
        public IStructuredResult result;
        @Argument
        public List<IStructuredResult> results;
        
        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Structured result: ").append(result.toString()).append("\n");
            sb.append("Structured results: "+results);
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }

    @Category("Test")
    @CommandName("supplement_test")
    @Name("Supplementary query")
    public static class SupplementTest implements IQuery
    {
        @Argument
        public ISnapshot snapshot;

        public IResult execute(IProgressListener listener) throws Exception
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Supplement");
            TextResult ret = new TextResult(sb.toString());
            return ret;
        }
    }
    
    @Category("Test")
    public static class SecondarySnapshotQuery implements IQuery
    {
        @Argument(advice = Advice.SECONDARY_SNAPSHOT)
        public ISnapshot sn;

        public IResult execute(IProgressListener listener) throws Exception
        {
            TextResult ret = new TextResult("Snapshot2 " + sn+" "+sn.getSnapshotInfo().getJvmInfo());
            return ret;
        }
    }
}
