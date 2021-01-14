/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial implementation
 *******************************************************************************/
/**
 * Create IBM dumps - this is an internal package not intended as an Application Programming Interface (API) except as an extension.
 * General plan:
 * DumpFactory - called by Acquire UI
 * returns either an IBMDumpProvider if the current VM is an IBM VM allowing late attach
 * or an IBMExecDumpProvider if the current VM is unsuitable and we need to start a new IBM VM to get the data
 * The DumpProvider returns a list of IBMVmInfo or IBMExecVmInfo objects
 * The VmInfo dump provider field should be the dump provider that provided the VmInfo, and must be a registered provider
 * so the help text works.
 * The acquire dialog selects a VMInfo provider and a file name, then calls the provider.
 * The IBMDumpProvider selects an appropriate delegation DumpProvider (System, Heap or Java) and calls the delegate to 
 * generate the dump and pack the result.
 * The IBMExecDumpProvider creates a helper jar then starts a child IBM VM to generate the dump and format the result.
*/
package org.eclipse.mat.ibmvm.acquire;
