/*******************************************************************************
 * Copyright (c) 2009, 2010 SAP AG.
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
 * @since 1.0
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

	/**
	 * Get the PID of the process
	 * 
	 * @return the process ID
	 */
	public int getPid()
	{
		return pid;
	}

	/**
	 * Set the PID for the process descriptor
	 * 
	 * @param pid
	 */
	public void setPid(int pid)
	{
		this.pid = pid;
	}

	/**
	 * Get the description of the Java process
	 * 
	 * @return the description
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Set the description of the Java process
	 * 
	 * @param description
	 */
	public void setDescription(String description)
	{
		this.description = description;
	}

	/**
	 * Indicate if a heap dump can be acquired from the described process
	 * 
	 * @return
	 */
	public boolean isHeapDumpEnabled()
	{
		return heapDumpEnabled;
	}

	/**
	 * Set the flag if heap dumps can be acquired from the described process
	 * 
	 * @param heapDumpEnabled
	 */
	public void setHeapDumpEnabled(boolean heapDumpEnabled)
	{
		this.heapDumpEnabled = heapDumpEnabled;
	}

	/**
	 * Get the heap dump provider which returned this VmInfo
	 * 
	 * @return
	 */
	public IHeapDumpProvider getHeapDumpProvider()
	{
		return heapDumpProvider;
	}

	/**
	 * Set the heap dump provider of this VmInfo
	 * 
	 * @param heapDumpProvider
	 */
	public void setHeapDumpProvider(IHeapDumpProvider heapDumpProvider)
	{
		this.heapDumpProvider = heapDumpProvider;
	}

	/**
	 * Returns a proposed file name under which the heap dump should be saved,
	 * e.g. java_pid%pid%.hprof for HPROF files
	 * 
	 * @return
	 */
	public String getProposedFileName()
	{
		return proposedFileName;
	}

	/**
	 * Set the proposed file name for this process
	 * @param proposedFileName
	 */
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
