/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ResultMetaData;

public class TaskInfo
{
    private static final String[] STATES = { Messages.TaskInfo_State_NotApplicable, //
                    Messages.TaskInfo_State_Idle, //
                    Messages.TaskInfo_State_Waiting, //
                    Messages.TaskInfo_State_Processing, //
                    Messages.TaskInfo_State_WaitingSyncIO };

    /* package */static class Result implements IResultTree
    {
        List<TaskInfo> tasks;

        public Result(List<TaskInfo> tasks)
        {
            this.tasks = tasks;
        }

        public ResultMetaData getResultMetaData()
        {
            return new ResultMetaData.Builder().setIsPreSortedBy(0, Column.SortDirection.DESC).build();
        }

        public Column[] getColumns()
        {
            return new Column[] { new Column(Messages.TaskInfo_Column_Number, String.class), //
                            new Column(Messages.TaskInfo_Column_Name, String.class), //
                            new Column(Messages.TaskInfo_Column_State, String.class), //
                            new Column(Messages.TaskInfo_Column_Id, String.class) };
        }

        public List<?> getElements()
        {
            return tasks;
        }

        public boolean hasChildren(Object element)
        {
            return !((TaskInfo) element).subtasks.isEmpty();
        }

        public List<?> getChildren(Object parent)
        {
            return ((TaskInfo) parent).subtasks;
        }

        public Object getColumnValue(Object row, int columnIndex)
        {
            TaskInfo task = (TaskInfo) row;
            switch (columnIndex)
            {
                case 0:
                    return task.getSequence();
                case 1:
                    return task.getName();
                case 2:
                    return task.getState();
                case 3:
                    return task.getId();
            }

            return null;
        }

        public IContextObject getContext(Object row)
        {
            return null;
        }

    }

    private String sequence;
    private String id;
    private String name;
    private int state;

    private LinkedList<TaskInfo> subtasks = new LinkedList<TaskInfo>();

    /* package */TaskInfo(String sequence, String id, String name, int state)
    {
        this.sequence = sequence;
        this.id = id;
        this.name = name != null ? name : "<>"; //$NON-NLS-1$
        this.state = state;
    }

    public String getSequence()
    {
        return sequence;
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getState()
    {
        if (state >= 0 && state < STATES.length)
            return STATES[state];
        return Messages.TaskInfo_State_NotApplicable;
    }

    public void addSubtask(TaskInfo subtask)
    {
        // subtasks are stored in reversed order
        subtasks.addFirst(subtask);
    }

    public List<TaskInfo> getSubtasks()
    {
        return subtasks;
    }
}
