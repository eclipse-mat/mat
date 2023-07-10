/*******************************************************************************
 * Copyright (c) 2009, 2023 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - validation of indices
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.parser.internal.messages"; //$NON-NLS-1$
    public static String AbstractObjectImpl_Error_FieldContainsIllegalReference;
    public static String AbstractObjectImpl_Error_FieldIsNotReference;
    public static String BitOutputStream_Error_ArrayFull;
    public static String ClassHistogramRecordBuilder_Error_IllegalUseOfHistogramBuilder;
    public static String DominatorTree_CalculateRetainedSizes;
    public static String DominatorTree_CalculatingDominatorTree;
    public static String DominatorTree_ComputingDominators;
    public static String DominatorTree_CreateDominatorsIndexFile;
    public static String DominatorTree_DepthFirstSearch;
    public static String DominatorTree_DominatorTreeCalculation;
    public static String Function_Error_NeedsNumberAsInput;
    public static String Function_ErrorNoFunction;
    public static String Function_unknown;
    public static String GarbageCleaner_ReIndexingClasses;
    public static String GarbageCleaner_ReIndexingObjects;
    public static String GarbageCleaner_ReIndexingOutboundIndex;
    public static String GarbageCleaner_RemovedUnreachableObjects;
    public static String GarbageCleaner_RemovingUnreachableObjects;
    public static String GarbageCleaner_SearchingForUnreachableObjects;
    public static String GarbageCleaner_Writing;
    public static String HistogramBuilder_Error_FailedToStoreInHistogram;
    public static String IndexReader_Error_IndexIsEmbedded;
    public static String IndexWriter_Error_ArrayLength;
    public static String IndexWriter_Error_ObjectArrayLength;
    public static String IndexWriter_NotImplemented;
    public static String IndexWriter_StoredError;
    public static String IndexWriter_StoredException;
    public static String MethodCallExpression_Error_MethodNotFound;
    public static String MethodCallExpression_Error_MethodProhibited;
    public static String MultiplePathsFromGCRootsComputerImpl_FindingPaths;
    public static String SnapshotFactoryImpl_ClassIDNotFound;
    public static String SnapshotFactoryImpl_ClassImplNotFound;
    public static String SnapshotFactoryImpl_ClassIndexAddressNoLoaderID;
    public static String SnapshotFactoryImpl_ClassIndexAddressNotEqualClassObjectAddress;
    public static String SnapshotFactoryImpl_ClassIndexAddressTypeIDNotEqualClassImplClassId;
    public static String SnapshotFactoryImpl_ClassIndexNotEqualClassObjectID;
    public static String SnapshotFactoryImpl_ConcurrentParsingError;
    public static String SnapshotFactoryImpl_EmptyOutbounds;
    public static String SnapshotFactoryImpl_Error_NoParserRegistered;
    public static String SnapshotFactoryImpl_Error_OpeningHeapDump;
    public static String SnapshotFactoryImpl_Error_ReparsingHeapDump;
    public static String SnapshotFactoryImpl_ErrorOpeningHeapDump;
    public static String SnapshotFactoryImpl_GCRootContextIDDoesNotMatchAddress;
    public static String SnapshotFactoryImpl_GCRootIDDoesNotMatchAddress;
    public static String SnapshotFactoryImpl_GCRootIDDoesNotMatchIndex;
    public static String SnapshotFactoryImpl_GCRootIDOutOfRange;
    public static String SnapshotFactoryImpl_GCThreadIDOutOfRange;
    public static String SnapshotFactoryImpl_GCThreadRootIDDoesNotMatchIndex;
    public static String SnapshotFactoryImpl_GCThreadRootIDOutOfRange;
    public static String SnapshotFactoryImpl_IndexAddressFoundAtOtherID;
    public static String SnapshotFactoryImpl_IndexAddressHasSameAddressAsPrevious;
    public static String SnapshotFactoryImpl_IndexAddressIsSmallerThanPrevious;
    public static String SnapshotFactoryImpl_IndexAddressNegativeArraySize;
    public static String SnapshotFactoryImpl_InvalidFirstOutbound;
    public static String SnapshotFactoryImpl_InvalidOutbound;
    public static String SnapshotFactoryImpl_MATParsingLock;
    public static String SnapshotFactoryImpl_NoOutbounds;
    public static String SnapshotFactoryImpl_ObjDescClass;
    public static String SnapshotFactoryImpl_ObjDescObjType;
    public static String SnapshotFactoryImpl_ObjDescObjTypeAddress;
    public static String SnapshotFactoryImpl_ObjectsFoundButClassesHadObjectsAndClassesInTotal;
    public static String SnapshotFactoryImpl_ReparsingHeapDumpAsIndexOutOfDate;
    public static String SnapshotFactoryImpl_ReparsingHeapDumpWithOutOfDateIndex;
    public static String SnapshotFactoryImpl_UnableToDeleteIndexFile;
    public static String SnapshotFactoryImpl_ValidatingGCRoots;
    public static String SnapshotFactoryImpl_ValidatingIndices;
    public static String SnapshotImpl_BuildingHistogram;
    public static String SnapshotImpl_CalculatingRetainedHeapSizeForClasses;
    public static String SnapshotImpl_Error_DomTreeNotAvailable;
    public static String SnapshotImpl_Error_ObjectNotFound;
    public static String SnapshotImpl_Error_ParserNotFound;
    public static String SnapshotImpl_Error_ReplacingNonExistentClassLoader;
    public static String SnapshotImpl_Error_UnknownVersion;
    public static String SnapshotImpl_Error_UnrecognizedState;
    public static String SnapshotImpl_Histogram;
    public static String SnapshotImpl_Label;
    public static String SnapshotImpl_ReadingInboundReferrers;
    public static String SnapshotImpl_ReadingOutboundReferrers;
    public static String SnapshotImpl_ReopeningParsedHeapDumpFile;
    public static String SnapshotImpl_RetrievingDominators;
    public static String ObjectArrayImpl_forArray;
    public static String ObjectMarker_MarkingObjects;
    public static String ObjectMarker_ErrorMarkingObjects;
    public static String ObjectMarker_WarningMarkingObjects;
    public static String ObjectMarker_ErrorMarkingObjectsSeeLog;
    public static String Operation_Error_ArgumentOfUnknownClass;
    public static String Operation_Error_CannotCompare;
    public static String Operation_Error_NotInArgumentOfUnknownClass;
    public static String Operation_Error_NotInCannotCompare;
    public static String Operation_ErrorNoComparable;
    public static String Operation_ErrorNotNumber;
    public static String OQLQueryImpl_CheckingClass;
    public static String OQLQueryImpl_CollectingObjects;
    public static String OQLQueryImpl_Error_CannotCalculateRetainedSet;
    public static String OQLQueryImpl_Error_ClassCastExceptionOccured;
    public static String OQLQueryImpl_Error_ElementIsNotClass;
    public static String OQLQueryImpl_Error_InvalidClassNamePattern;
    public static String OQLQueryImpl_Error_MissingSnapshot;
    public static String OQLQueryImpl_Error_MustReturnObjectList;
    public static String OQLQueryImpl_Error_QueryCannotBeConverted;
    public static String OQLQueryImpl_Error_QueryMustHaveIdenticalSelectItems;
    public static String OQLQueryImpl_Error_QueryMustReturnObjects;
    public static String OQLQueryImpl_Error_ResultMustReturnObjectList;
    public static String OQLQueryImpl_Errot_IsNotClass;
    public static String OQLQueryImpl_Selecting;
    public static String OQLQueryImpl_SelectingObjects;
    public static String ParserRegistry_ErrorCompilingFileNamePattern;
    public static String ParserRegistry_ErrorWhileCreating;
    public static String PathExpression_Error_ArrayHasNoProperty;
    public static String PathExpression_Error_TypeHasNoProperty;
    public static String PathExpression_Error_UnknownElementInPath;
    public static String PositionInputStream_mark;
    public static String PositionInputStream_reset;
    public static String PositionInputStream_seek;
    public static String RetainedSizeCache_ErrorReadingRetainedSizes;
    public static String RetainedSizeCache_Warning_IgnoreError;

    public static String OQLParser_Encountered_X_at_line_X_column_X_Was_expecting_one_of_X;
    public static String OQLParser_Missing_return_statement_in_function;

    public static String ThreadStackHelper_InvalidThread;
    public static String ThreadStackHelper_InvalidThreadLocal;

    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}
