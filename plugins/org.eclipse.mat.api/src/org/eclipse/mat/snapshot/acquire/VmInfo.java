/*******************************************************************************
 * Copyright (c) 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot.acquire;

/**
 * Instances of this class are descriptors of locally running Java processes
 * 
 * @author ktsvetkov
 * 
 */
public class VmInfo
{
	private int pid;
	private String description;
	private boolean heapDumpEnabled;
	private String proposedFileName;

	private IHeapDumpProvider heapDumpProvider;

	/**
	 * An empty constructor
	 */
	public VmInfo()
	{}

	/**
	 * Constructor with parameters
	 * 
	 * @param pid
	 *            the process ID of the process
	 * @param description
	 *            a free text description of the process, usually the process
	 *            name
	 * @param heapDumpEnabled
	 *            a boolean value indicating if a heap dump from the process can
	 *            be acquired
	 * @param proposedFileName
	 *            a proposal for the file name, under which the heap dump can be
	 *            saved. %pid% can be used as a placeholder for the PID.
	 *            Example: java_pid%pid%.hprof
	 * @param heapDumpProvider
	 *            the {@link IHeapDumpProvider} which can use this VmInfo
	 */
	public VmInfo(int pid, String description, boolean heapDumpEnabled, String proposedFileName, IHeapDumpProvider heapDumpProvider)
	{
		super();
		this.pid = pid;
		this.description = description;
		this.heapDumpEnabled = heapDumpEnabled;
		this.proposedFileName = proposedFileName;
		this.heapDumpProvider = heapDumpProvider;
	}

	public int getPid()
	{
		return pid;
	}

	public void setPid(int pid)
	{
		this.pid = pid;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	public boolean isHeapDumpEnabled()
	{
		return heapDumpEnabled;
	}

	public void setHeapDumpEnabled(boolean heapDumpEnabled)
	{
		this.heapDumpEnabled = heapDumpEnabled;
	}

	public IHeapDumpProvider getHeapDumpProvider()
	{
		return heapDumpProvider;
	}

	public void setHeapDumpProvider(IHeapDumpProvider heapDumpProvider)
	{
		this.heapDumpProvider = heapDumpProvider;
	}

	public String getProposedFileName()
	{
		return proposedFileName;
	}

	public void setProposedFileName(String proposedFileName)
	{
		this.proposedFileName = proposedFileName;
	}

	@Override
	public String toString()
	{
		return "PID = " + pid + "\t" + description;
	}

}
