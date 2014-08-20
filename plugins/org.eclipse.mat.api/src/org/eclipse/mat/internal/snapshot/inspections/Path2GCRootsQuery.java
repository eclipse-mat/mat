/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot.inspections;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.ISelectionProvider;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Menu;
import org.eclipse.mat.query.annotations.Menu.Entry;
import org.eclipse.mat.snapshot.IPathsFromGCRootsComputer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.mat.util.IProgressListener;

@CommandName("path2gc")
@Icon("/META-INF/icons/path2gc.gif")
@Menu( { @Entry(options = "-excludes \"\""), //
                @Entry(options = "-excludes java.lang.ref.WeakReference:referent"), //
                @Entry(options = "-excludes java.lang.ref.SoftReference:referent"), //
                @Entry(options = "-excludes java.lang.ref.PhantomReference:referent"), //
                @Entry(options = "-excludes java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent"), //
                @Entry(options = "-excludes java.lang.ref.PhantomReference:referent java.lang.ref.SoftReference:referent"), //                
                @Entry(options = "-excludes java.lang.ref.PhantomReference:referent java.lang.ref.WeakReference:referent"), //
                @Entry(options = "-excludes java.lang.ref.Reference:referent") //
})
@HelpUrl("/org.eclipse.mat.ui.help/reference/inspections/path_to_gc_roots.html")
public class Path2GCRootsQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED, advice = Argument.Advice.HEAP_OBJECT)
    public int object;

    @Argument(isMandatory = false)
    public List<String> excludes = Arrays.asList( //
                    new String[] { "java.lang.ref.WeakReference:referent", "java.lang.ref.SoftReference:referent" }); //$NON-NLS-1$ //$NON-NLS-2$

    @Argument(isMandatory = false)
    public int numberOfPaths = 30;

    public IResult execute(IProgressListener listener) throws Exception
    {
        // convert excludes into the required format
        Map<IClass, Set<String>> excludeMap = convert(snapshot, excludes);

        // create result tree
        IPathsFromGCRootsComputer computer = snapshot.getPathsFromGCRoots(object, excludeMap);
        Tree result = new Tree(snapshot, object, computer);
        result.addInitialPaths(Math.max(1, numberOfPaths), listener);

        return result;
    }

    protected static Map<IClass, Set<String>> convert(ISnapshot snapshot, List<String> excludes)
                    throws SnapshotException
    {
        Map<IClass, Set<String>> excludeMap = null;

        if (excludes != null && !excludes.isEmpty())
        {
            excludeMap = new HashMap<IClass, Set<String>>();

            for (String entry : excludes)
            {
                String pattern = entry;
                Set<String> fields = null;
                int colon = entry.indexOf(':');

                if (colon >= 0)
                {
                    fields = new HashSet<String>();

                    StringTokenizer tokens = new StringTokenizer(entry.substring(colon + 1), ","); //$NON-NLS-1$
                    while (tokens.hasMoreTokens())
                        fields.add(tokens.nextToken());

                    pattern = pattern.substring(0, colon);
                }

                for (IClass clazz : snapshot.getClassesByName(Pattern.compile(pattern), true))
                    excludeMap.put(clazz, fields);
            }
        }

        return excludeMap;
    }

    // //////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////

    private static class Node
    {
        int objectId;

        List<Node> children;

        String label;
        String gcRoots;
        Bytes shallowHeap;
        Bytes retainedHeap;

        boolean isExpanded;
        boolean isSelected;

        public Node(int objectId)
        {
            this.objectId = objectId;
            this.shallowHeap = new Bytes(-1);
            this.retainedHeap = new Bytes(-1);
        }

        /* package */Node getChild(int childId)
        {
            Node child = null;

            if (children == null)
            {
                children = new ArrayList<Node>();
            }
            else
            {
                for (Node c : children)
                {
                    if (c.objectId == childId)
                    {
                        child = c;
                        break;
                    }
                }
            }

            return child;
        }

        /* package */Node addChild(int childId)
        {
            ChildNode child = new ChildNode(this, childId);
            children.add(child);
            return child;
        }

    }

    private static class ChildNode extends Node
    {
        Node parent;
        String attribute;

        private ChildNode(Node parent, int objectId)
        {
            super(objectId);
            this.parent = parent;
        }
    }

    public final static class Tree implements IResultTree, IIconProvider, IDecorator, ISelectionProvider
    {
        ISnapshot snapshot;
        IPathsFromGCRootsComputer computer;
        Node root;
        int noOfPathsFound;

        public Tree(ISnapshot snapshot, int objectId, IPathsFromGCRootsComputer computer)
        {
            this.snapshot = snapshot;
            this.computer = computer;

            this.root = new Node(objectId);
            this.noOfPathsFound = 0;
        }

        /**
         * @return the ancestors of the first newly created object
         */
        public List<?> addNextPath() throws SnapshotException
        {
            if (computer == null)
                return null;

            int[] path = computer.getNextShortestPath();
            if (path == null)
            {
                computer = null;
                return null;
            }

            List<Object> ancestors = new ArrayList<Object>();
            boolean fatherToBeFound = false;

            noOfPathsFound++;

            Node current = root;
            for (int pos = 1; pos < path.length; pos++)
            {
                Node child = current.getChild(path[pos]);
                if (child == null)
                {
                    child = current.addChild(path[pos]);
                    if (!fatherToBeFound)
                        ancestors.add(current);
                    fatherToBeFound = true;
                }
                else
                {
                    ancestors.add(current);
                }

                current = child;
            }

            return ancestors;
        }

        public boolean morePathsAvailable()
        {
            return computer != null;
        }

        public int getNumberOfPaths()
        {
            return noOfPathsFound;
        }

        private void addInitialPaths(int noOfNextPaths, IProgressListener listener) throws SnapshotException
        {
            if (computer == null)
                return;

            root.isExpanded = true;

            for (int ii = 0; ii < noOfNextPaths; ii++)
            {
                int[] path = computer.getNextShortestPath();
                if (path == null)
                {
                    computer = null;
                    break;
                }
                add(path, ii == 0);

                if (listener.isCanceled())
                    return;
            }
        }

        private void add(int[] path, boolean expandAndSelect)
        {
            noOfPathsFound++;

            Node current = root;
            for (int pos = 1; pos < path.length; pos++)
            {
                Node child = current.getChild(path[pos]);
                if (child == null)
                    child = current.addChild(path[pos]);

                if (expandAndSelect)
                    child.isExpanded = true;

                current = child;
            }

            if (expandAndSelect)
                current.isSelected = true;
        }

        public ResultMetaData getResultMetaData()
        {
            return null;
        }

        public final Column[] getColumns()
        {
            return new Column[] { new Column(Messages.Column_ClassName).decorator(this), //
                            new Column(Messages.Column_ShallowHeap, Bytes.class).noTotals(), //
                            new Column(Messages.Column_RetainedHeap, Bytes.class).noTotals() };
        }

        public List<?> getElements()
        {
            List<Node> answer = new ArrayList<Node>(1);
            answer.add(root);
            return answer;
        }

        public List<?> getChildren(Object parent)
        {
            return ((Node) parent).children;
        }

        public boolean hasChildren(Object element)
        {
            return ((Node) element).children != null;
        }

        public final Object getColumnValue(Object row, int columnIndex)
        {
            try
            {
                Node node = (Node) row;

                switch (columnIndex)
                {
                    case 0:
                        if (node.label == null)
                        {
                            IObject obj = snapshot.getObject(node.objectId);
                            node.label = obj.getDisplayName();
                            node.shallowHeap = new Bytes(obj.getUsedHeapSize());
                        }
                        return node.label;
                    case 1:
                        if (node.shallowHeap.getValue() == -1)
                            node.shallowHeap = new Bytes(snapshot.getHeapSize(node.objectId));
                        return node.shallowHeap;
                    case 2:
                        if (node.retainedHeap.getValue() == -1)
                            node.retainedHeap = new Bytes(snapshot.getRetainedHeapSize(node.objectId));
                        return node.retainedHeap;
                }
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }

            return null;
        }

        public final IContextObject getContext(final Object row)
        {
            return new IContextObject()
            {
                public int getObjectId()
                {
                    return ((Node) row).objectId;
                }
            };
        }

        public URL getIcon(Object row)
        {
            if (row instanceof ChildNode)
                return Icons.inbound(snapshot, ((Node) row).objectId);
            else
                return Icons.forObject(snapshot, ((Node) row).objectId);
        }

        public boolean isExpanded(Object row)
        {
            return ((Node) row).isExpanded;
        }

        public boolean isSelected(Object row)
        {
            return ((Node) row).isSelected;
        }

        public final String prefix(Object row)
        {
            if (row instanceof ChildNode)
            {
                ChildNode node = (ChildNode) row;
                if (node.attribute == null)
                    fillInAttribute(node);

                return node.attribute;
            }
            else
            {
                return null;
            }
        }

        private void fillInAttribute(ChildNode node)
        {
            try
            {
                IObject heapObject = snapshot.getObject(node.objectId);

                long parentAddress = snapshot.mapIdToAddress(node.parent.objectId);

                StringBuilder s = new StringBuilder(64);

                List<NamedReference> refs = heapObject.getOutboundReferences();
                for (NamedReference reference : refs)
                {
                    if (reference.getObjectAddress() == parentAddress)
                    {
                        if (s.length() > 0)
                            s.append(", "); //$NON-NLS-1$
                        s.append(reference.getName());
                    }
                }

                node.attribute = s.toString();
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }

        public final String suffix(Object row)
        {
            try
            {
                Node node = (Node) row;
                if (node.gcRoots == null)
                {
                    if (snapshot.isGCRoot(node.objectId))
                        node.gcRoots = GCRootInfo.getTypeSetAsString(snapshot.getGCRootInfo(node.objectId));
                }

                return node.gcRoots;
            }
            catch (SnapshotException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
