/*******************************************************************************
 * Copyright (c) 2008, 2023 SAP AG, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson (IBM Corporation) - discard options
 *******************************************************************************/
package org.eclipse.mat.ui.snapshot;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.ui.MemoryAnalyserPlugin;
import org.eclipse.mat.ui.Messages;
import org.eclipse.mat.ui.SnapshotHistoryService;
import org.eclipse.mat.ui.internal.PreferenceConstants;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;

public abstract class ParseHeapDumpJob extends Job
{
    private final IPath path;
    private final Map<String, String> arguments;
    private final Display display;
    
    private static Map<String, String> defaultArguments()
    {
        Map<String, String> args = new HashMap<String, String>();
        IPreferenceStore prefs = MemoryAnalyserPlugin.getDefault().getPreferenceStore();
        if (prefs.getBoolean(PreferenceConstants.P_KEEP_UNREACHABLE_OBJECTS))
        {
            args.put("keep_unreachable_objects", Boolean.TRUE.toString()); //$NON-NLS-1$
        }
        if (prefs.getBoolean(PreferenceConstants.DISCARD_ENABLE))
        {
            int ratio = prefs.getInt(PreferenceConstants.DISCARD_RATIO);
            args.put("discard_ratio", Integer.toString(ratio)); //$NON-NLS-1$
            int offset = prefs.getInt(PreferenceConstants.DISCARD_OFFSET);
            args.put("discard_offset", Integer.toString(offset)); //$NON-NLS-1$
            String pattern = prefs.getString(PreferenceConstants.DISCARD_PATTERN);
            args.put("discard_pattern", pattern); //$NON-NLS-1$
            int seed = prefs.getInt(PreferenceConstants.DISCARD_SEED);
            args.put("discard_seed", Integer.toString(seed)); //$NON-NLS-1$
        }
        return args;
    }

    public ParseHeapDumpJob(IPath path, Display display)
    {
        this(path, defaultArguments(), display);
    }

    protected ParseHeapDumpJob(IPath path, Map<String, String> args, Display display)
        {
        super(MessageUtil.format(Messages.ParseHeapDumpJob_ParsingHeapDumpFrom, path.toOSString()));
        this.path = path;
        this.arguments = args;
        this.display = display;
        this.setUser(true);

        this.setRule(new ParseRule(path));
    }

    protected IStatus run(IProgressMonitor monitor)
    {
        this.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.TRUE);

        try
        {
            SnapshotHistoryService.getInstance().addVisitedPath(MemoryAnalyserPlugin.EDITOR_ID, path.toOSString());

            ISnapshot snap = null;
            try 
            {
                snap = SnapshotFactory.openSnapshot(path.toFile(), arguments, new ProgressMonitorWrapper(monitor));
            }
            catch (final MultipleSnapshotsException mre) 
            {
                // Prompt user to select a runtimeId and retry
                RuntimeSelector runtimeSelector = new RuntimeSelector(mre, display);
                String selectedId = runtimeSelector.getSelectedRuntimeId();
                if (selectedId != null)
                {
                    arguments.put("snapshot_identifier", selectedId); //$NON-NLS-1$
                    snap = SnapshotFactory.openSnapshot(path.toFile(), arguments, new ProgressMonitorWrapper(monitor));
                }
            }

            if (snap == null)
            {
                return Status.CANCEL_STATUS;
            }
            else
            {
                final ISnapshot snapshot = snap;
                // copy snapshot info -> needed for serialization of file
                // history
                SnapshotInfo source = snapshot.getSnapshotInfo();
                SnapshotInfo destination = new SnapshotInfo(source.getPath(), source.getPrefix(), source.getJvmInfo(),
                                source.getIdentifierSize(), source.getCreationDate(), source.getNumberOfObjects(),
                                source.getNumberOfGCRoots(), source.getNumberOfClasses(), source
                                                .getNumberOfClassLoaders(), source.getUsedHeapSize());
                // This properties are needed for the outline view , but don't copy all properties e.g. UnreachableObjectsHistogram
                copyPropertyIfSet(source, destination, "$heapFormat"); //$NON-NLS-1$
                copyPropertyIfSet(source, destination, "$useCompressedOops"); //$NON-NLS-1$
                copyPropertyIfSet(source, destination, "$runtimeId"); //$NON-NLS-1$
                SnapshotHistoryService.getInstance().addVisitedPath(MemoryAnalyserPlugin.EDITOR_ID, path.toOSString(),
                                destination);

                display.asyncExec(new Runnable()
                {
                    public void run()
                    {
                        ParseHeapDumpJob.this.finished(snapshot);
                    }
                });

                return Status.OK_STATUS;
            }
        }
        catch (IProgressListener.OperationCanceledException e)
        {
            return Status.CANCEL_STATUS;
        }
        catch (SnapshotException e)
        {
            return ErrorHelper.createErrorStatus(e);
        }
    }

    private void copyPropertyIfSet(SnapshotInfo source, SnapshotInfo destination, String key)
    {
        Serializable value = source.getProperty(key);
        if (value != null)
            destination.setProperty(key, value);
    }

    public boolean isModal()
    {
        Boolean isModal = (Boolean) this.getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
        if (isModal == null)
            return false;
        return isModal.booleanValue();
    }

    protected abstract void finished(ISnapshot snapshot);

    // //////////////////////////////////////////////////////////////
    // internal classes
    // //////////////////////////////////////////////////////////////

    static class ParseRule implements ISchedulingRule
    {
        IPath path;

        public ParseRule(IPath filename)
        {
            this.path = filename;
        }

        public boolean contains(ISchedulingRule rule)
        {
            return rule instanceof ParseRule && ((ParseRule) rule).path.equals(path);
        }

        public boolean isConflicting(ISchedulingRule rule)
        {
            return contains(rule);
        }

    }

}
