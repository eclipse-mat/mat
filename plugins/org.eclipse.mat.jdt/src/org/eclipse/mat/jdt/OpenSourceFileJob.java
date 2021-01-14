/*******************************************************************************
 * Copyright (c) 2008, 2020 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - get display for RAP
 *******************************************************************************/
package org.eclipse.mat.jdt;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.MethodNameMatch;
import org.eclipse.jdt.core.search.MethodNameMatchRequestor;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.ui.actions.OpenAction;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleStringTokenizer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;

public class OpenSourceFileJob extends Job
{
    private final String className;

    private String packageName, typeName, methodName, signature;

    private Object[] innerTypes;

    private List<IMember> matches;

    private Display display;

    public OpenSourceFileJob(String className, String methodName, String signature, Display display)
    {
        super(MessageUtil.format(Messages.OpenSourceFileJob_LookingFor, className));
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.setUser(true);
        this.display = display;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor)
    {
        try
        {
            preparePattern();
            if (methodName != null)
                collectMethodMatches(monitor);
            if (matches == null || matches.isEmpty())
                collectMatches(monitor);
            displayResult();

            return Status.OK_STATUS;
        }
        catch (JavaModelException e)
        {
            return new Status(Status.ERROR, "org.eclipse.mat.jdt", 0, e.getLocalizedMessage(), e); //$NON-NLS-1$
        }
    }

    private void preparePattern()
    {
        int dot = className.lastIndexOf('.');
        int inner = className.indexOf('$');
        int end = className.indexOf('[');

        if (end < 0)
            end = className.length();

        this.packageName = dot >= 0 ? className.substring(0, dot) : null;

        if (inner < 0)
            this.typeName = dot >= 0 ? className.substring(dot + 1, end) : className;
        else
            this.typeName = dot >= 0 ? className.substring(dot + 1, inner) : className.substring(0, inner);

        if (inner >= 0)
        {
            String names = className.substring(inner + 1, end);
            String[] types = SimpleStringTokenizer.split(names, '$');

            this.innerTypes = new Object[types.length];
            for (int ii = 0; ii < types.length; ii++)
            {
                try
                {
                    this.innerTypes[ii] = Integer.parseInt(types[ii]);
                }
                catch (NumberFormatException ignore)
                {
                    this.innerTypes[ii] = types[ii];
                }
            }
        }
    }

    private void collectMatches(IProgressMonitor monitor) throws JavaModelException
    {
        matches = new ArrayList<IMember>();

        new SearchEngine().searchAllTypeNames(packageName != null ? packageName.toCharArray() : null, //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        typeName.toCharArray(), //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        IJavaSearchConstants.TYPE, //
                        SearchEngine.createWorkspaceScope(), //
                        new TypeNameMatchRequestor()
                        {
                            @Override
                            public void acceptTypeNameMatch(TypeNameMatch match)
                            {
                                try
                                {
                                    IType type = match.getType();
                                    type = resolveInnerTypes(type);
                                    matches.add(type);
                                }
                                catch (JavaModelException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            }

                        }, //
                        IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, //
                        monitor);
    }

    private void collectMethodMatches(IProgressMonitor monitor) throws JavaModelException
    {
        matches = new ArrayList<IMember>();

        new SearchEngine().searchAllMethodNames(packageName != null ? packageName.toCharArray() : null, //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        null, //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        typeName.toCharArray(), //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        methodName.toCharArray(), //
                        SearchPattern.R_FULL_MATCH | SearchPattern.R_CASE_SENSITIVE, //
                        SearchEngine.createWorkspaceScope(), //
                        new MethodNameMatchRequestor()
                        {
                            @Override
                            public void acceptMethodNameMatch(MethodNameMatch match)
                            {
                                try
                                {
                                    IMethod m = match.getMethod();
                                    // Exact match of signature goes to top
                                    if (m.getSignature().equals(signature))
                                        matches.add(0, m);
                                    else
                                        matches.add(m);
                                    IType type = m.getDeclaringType();
                                    type = resolveInnerTypes(type);
                                }
                                catch (JavaModelException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            }

                        }, //
                        IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, //
                        monitor);
    }

    private IType resolveInnerTypes(IType match) throws JavaModelException
    {
        if (this.innerTypes == null)
            return match;

        for (final Object innerType : innerTypes)
        {
            Stack<IJavaElement> stack = new Stack<IJavaElement>();
            stack.push(match);

            while (!stack.isEmpty())
            {
                IJavaElement subject = stack.pop();

                if (subject instanceof IType)
                {
                    IType type = (IType) subject;
                    if (innerType instanceof Integer)
                    {
                        if (type.isAnonymous() && type.getOccurrenceCount() == (Integer) innerType)
                        {
                            match = type;
                            break;
                        }
                    }
                    else
                    {
                        if (innerType.equals(type.getElementName()))
                        {
                            match = type;
                            break;
                        }
                    }
                }

                if (subject instanceof IParent)
                    for (IJavaElement child : ((IParent) subject).getChildren())
                        stack.push(child);
            }
        }

        return match;
    }

    private void displayResult()
    {
        display.asyncExec(new Runnable()
        {
            public void run()
            {
                if (matches.isEmpty())
                {
                    MessageBox box = new MessageBox(display.getActiveShell(),
                                    SWT.ICON_INFORMATION);
                    box.setText(Messages.OpenSourceFileJob_NotFound);
                    box.setMessage(MessageUtil.format(Messages.OpenSourceFileJob_TypeNotFound, className));
                    box.open();
                }
                else if (matches.size() == 1)
                {
                    IMember member = matches.get(0);
                    openSourceFile(member);
                }
                else
                {
                    IMember type = selectType(matches);
                    if (type != null)
                        openSourceFile(type);
                }
            }
        });
    }

    private IMember selectType(List<IMember> matches)
    {
        ListDialog dialog = new ListDialog(display.getActiveShell());

        dialog.setLabelProvider(new LabelProvider()
        {
            public Image getImage(Object element)
            {
                return null;
            }

            public String getText(Object element)
            {
                StringBuilder buf = new StringBuilder(256);
                if (element instanceof IType)
                {
                    IType type = (IType) element;
                    buf.append(type.getElementName());
                    if (type.getPackageFragment() != null)
                        buf.append(" - ").append(type.getPackageFragment().getElementName()); //$NON-NLS-1$
                    if (type.getJavaProject() != null)
                        buf.append(" - ").append(type.getJavaProject().getElementName()); //$NON-NLS-1$
                }
                else if (element instanceof IMethod)
                {
                    IMethod m = (IMethod) element;
                    buf.append(m.getElementName());
                    try
                    {
                        buf.append(m.getSignature());
                    }
                    catch (JavaModelException e)
                    {

                    }
                    buf.append(" - "); //$NON-NLS-1$
                    IType type = m.getDeclaringType();
                    buf.append(type.getElementName());
                    if (type.getPackageFragment() != null)
                        buf.append(" - ").append(type.getPackageFragment().getElementName()); //$NON-NLS-1$
                    if (type.getJavaProject() != null)
                        buf.append(" - ").append(type.getJavaProject().getElementName()); //$NON-NLS-1$
                }
                return buf.toString();
            }
        });

        dialog.setContentProvider(new IStructuredContentProvider()
        {
            public Object[] getElements(Object inputElement)
            {
                return ((List<?>) inputElement).toArray();
            }

            public void dispose()
            {}

            public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
            {}

        });
        dialog.setInput(matches);
        dialog.setTitle(Messages.OpenSourceFileJob_SelectFile);
        dialog.setMessage(Messages.OpenSourceFileJob_SelectFileToOpen);
        dialog.open();

        Object[] result = dialog.getResult();

        return result == null ? null : (IMember) result[0];
    }

    private boolean openSourceFile(IJavaElement element)
    {
        if (element == null)
            return false;

        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

        if (page == null)
            return false;

        try
        {
            StructuredSelection ss = new StructuredSelection(element);
            OpenAction openAction = new OpenAction(page.getActivePart().getSite());
            openAction.run(ss);
            return true;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
