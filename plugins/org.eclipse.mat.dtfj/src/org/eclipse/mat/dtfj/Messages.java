/*******************************************************************************
 * Copyright (c) 2009,2011 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.dtfj;

import org.eclipse.osgi.util.NLS;

/**
 * Messages for the DTFJ parser.
 * @author ajohnson
 *
 */
public class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.dtfj.messages"; //$NON-NLS-1$
    public static String DTFJHeapObjectReader_ErrorReadingObjectArray;
    public static String DTFJHeapObjectReader_ErrorReadingObjectAtIndex;
    public static String DTFJHeapObjectReader_ErrorReadingPrimitiveArray;
    public static String DTFJHeapObjectReader_JavaObjectAtAddressNotFound;
    public static String DTFJHeapObjectReader_UnexpectedType;
    public static String DTFJHeapObjectReader_UnexpectedTypeName;
    public static String DTFJIndexBuilder_AddingExtraClassOfUnknownNameViaCachedList;
    public static String DTFJIndexBuilder_AddingExtraClassViaCachedList;
    public static String DTFJIndexBuilder_AddingMissingObjects;
    public static String DTFJIndexBuilder_BuildingClasses;
    public static String DTFJIndexBuilder_CheckRefsBootLoader;
    public static String DTFJIndexBuilder_CheckRefsClass;
    public static String DTFJIndexBuilder_CheckRefsObject;
    public static String DTFJIndexBuilder_ClassAtAddressNotFound;
    public static String DTFJIndexBuilder_ClassHasNoAddress;
    public static String DTFJIndexBuilder_ClassIDNotFound;
    public static String DTFJIndexBuilder_ClassImplNotFound;
    public static String DTFJIndexBuilder_ClassIndexAddressNoLoaderID;
    public static String DTFJIndexBuilder_ClassIndexAddressNotEqualClassObjectAddress;
    public static String DTFJIndexBuilder_ClassIndexAddressTypeIDNotEqualClassImplClassId;
    public static String DTFJIndexBuilder_ClassIndexNotEqualClassObjectID;
    public static String DTFJIndexBuilder_ClassLoaderAtAddressNotFound;
    public static String DTFJIndexBuilder_ClassLoaderObjectNotFound;
    public static String DTFJIndexBuilder_ClassLoaderObjectNotFoundType;
    public static String DTFJIndexBuilder_CorruptDataReadingAddressSpaces;
    public static String DTFJIndexBuilder_CorruptDataReadingBytecodeSections;
    public static String DTFJIndexBuilder_CorruptDataReadingCachedClasses;
    public static String DTFJIndexBuilder_CorruptDataReadingClasses;
    public static String DTFJIndexBuilder_CorruptDataReadingClassLoaders;
    public static String DTFJIndexBuilder_CorruptDataReadingClassLoaders1;
    public static String DTFJIndexBuilder_CorruptDataReadingCompiledCodeSections;
    public static String DTFJIndexBuilder_CorruptDataReadingCompiledSections;
    public static String DTFJIndexBuilder_CorruptDataReadingConstantPool;
    public static String DTFJIndexBuilder_CorruptDataReadingConstantPoolReferences;
    public static String DTFJIndexBuilder_CorruptDataReadingDeclaredFields;
    public static String DTFJIndexBuilder_CorruptDataReadingDeclaredMethods;
    public static String DTFJIndexBuilder_CorruptDataReadingHeaps;
    public static String DTFJIndexBuilder_CorruptDataReadingHeapSections;
    public static String DTFJIndexBuilder_CorruptDataReadingJavaStackFrames;
    public static String DTFJIndexBuilder_CorruptDataReadingJavaStackSections;
    public static String DTFJIndexBuilder_CorruptDataReadingMonitors;
    public static String DTFJIndexBuilder_CorruptDataReadingNativeStackFrames;
    public static String DTFJIndexBuilder_CorruptDataReadingObjects;
    public static String DTFJIndexBuilder_CorruptDataReadingProcesses;
    public static String DTFJIndexBuilder_CorruptDataReadingReferences;
    public static String DTFJIndexBuilder_CorruptDataReadingRoots;
    public static String DTFJIndexBuilder_CorruptDataReadingRuntimes;
    public static String DTFJIndexBuilder_CorruptDataReadingThreads;
    public static String DTFJIndexBuilder_CorruptDataReadingThreadsFromMonitors;
    public static String DTFJIndexBuilder_DeclaringClassNotFound;
    public static String DTFJIndexBuilder_DTFJDoesNotSupportHeapRoots;
    public static String DTFJIndexBuilder_DTFJgetHeapRootsFromStackFrameReturnsNull;
    public static String DTFJIndexBuilder_DTFJgetHeapRootsReturnsNull;
    public static String DTFJIndexBuilder_DTFJGetReferencesExtraID;
    public static String DTFJIndexBuilder_DTFJGetReferencesMissingAllReferences;
    public static String DTFJIndexBuilder_DTFJGetReferencesMissingID;
    public static String DTFJIndexBuilder_DTFJIndexBuilder_CorruptDataReadingNativeStackSection;
    public static String DTFJIndexBuilder_DTFJJavaRuntime;
    public static String DTFJIndexBuilder_DTFJRootsDisabled;
    public static String DTFJIndexBuilder_DuplicateJavaStackFrame;
    public static String DTFJIndexBuilder_ErrorGettingOutboundReferences;
    public static String DTFJIndexBuilder_ErrorReadingProcessID;
    public static String DTFJIndexBuilder_ExceptionGettingOutboundReferences;
    public static String DTFJIndexBuilder_FinalizableObjectsMarkedAsRoots;
    public static String DTFJIndexBuilder_FindingAllMethods;
    public static String DTFJIndexBuilder_FindingClasses;
    public static String DTFJIndexBuilder_FindingClassesCachedByClassLoaders;
    public static String DTFJIndexBuilder_FindingClassLoaderObjects;
    public static String DTFJIndexBuilder_FindingClassLoaders;
    public static String DTFJIndexBuilder_FindingJVM;
    public static String DTFJIndexBuilder_FindingMonitorObjects;
    public static String DTFJIndexBuilder_FindingObjects;
    public static String DTFJIndexBuilder_FindingOutboundReferencesForClasses;
    public static String DTFJIndexBuilder_FindingOutboundReferencesForObjects;
    public static String DTFJIndexBuilder_FindingRoots;
    public static String DTFJIndexBuilder_FindingRootsFromDTFJ;
    public static String DTFJIndexBuilder_FindingThreadObjectsMissingFromHeap;
    public static String DTFJIndexBuilder_FoundIdentifiersObjectsClasses;
    public static String DTFJIndexBuilder_FoundIdentifiersObjectsClassesMethods;
    public static String DTFJIndexBuilder_GCRootIDDoesNotMatchIndex;
    public static String DTFJIndexBuilder_GCRootIDOutOfRange;
    public static String DTFJIndexBuilder_GeneratingExtraRootsFromFinalizables;
    public static String DTFJIndexBuilder_GeneratingExtraRootsMarkingAllUnreferenced;
    public static String DTFJIndexBuilder_GeneratingGlobalRoots;
    public static String DTFJIndexBuilder_GeneratingMonitorRoots;
    public static String DTFJIndexBuilder_GeneratingSystemRoots;
    public static String DTFJIndexBuilder_GeneratingThreadRoots;
    public static String DTFJIndexBuilder_HighestMemoryAddressFromAddressSpaceIsUnaccessibleFromPointers;
    public static String DTFJIndexBuilder_HugeJavaStackSection;
    public static String DTFJIndexBuilder_HugeNativeStackSection;
    public static String DTFJIndexBuilder_IgnoringExtraJavaRuntime;
    public static String DTFJIndexBuilder_IgnoringJavaStackFrame;
    public static String DTFJIndexBuilder_IgnoringManagedRuntime;
    public static String DTFJIndexBuilder_ImageAddressSpaceEqualsBroken;
    public static String DTFJIndexBuilder_IndexAddressFoundAtOtherID;
    public static String DTFJIndexBuilder_IndexAddressHasSameAddressAsPrevious;
    public static String DTFJIndexBuilder_IndexAddressIsSmallerThanPrevious;
    public static String DTFJIndexBuilder_IndexAddressNegativeArraySize;
    public static String DTFJIndexBuilder_InterfaceShouldNotHaveASuperclass;
    public static String DTFJIndexBuilder_InvalidArrayElement;
    public static String DTFJIndexBuilder_InvalidField;
    public static String DTFJIndexBuilder_InvalidObjectFieldReference;
    public static String DTFJIndexBuilder_InvalidStaticField;
    public static String DTFJIndexBuilder_JVMFullVersion;
    public static String DTFJIndexBuilder_JVMVersion;
    public static String DTFJIndexBuilder_MATRootTypeUnknown;
    public static String DTFJIndexBuilder_MethodHasNoAddress;
    public static String DTFJIndexBuilder_MethodHasNonUniqueAddress;
    public static String DTFJIndexBuilder_MonitorObjectNotFound;
    public static String DTFJIndexBuilder_NativeStackFrameNotFound;
    public static String DTFJIndexBuilder_NativeThreadNotFound;
    public static String DTFJIndexBuilder_NoClassLoader;
    public static String DTFJIndexBuilder_NoDateInImage;
    public static String DTFJIndexBuilder_NoDTFJRootsFound;
    public static String DTFJIndexBuilder_NoRuntimeFullVersionFound;
    public static String DTFJIndexBuilder_NoRuntimeVersionFound;
    public static String DTFJIndexBuilder_NoSuperclassForArray;
    public static String DTFJIndexBuilder_NullClassImpl;
    public static String DTFJIndexBuilder_NullPurgedMapping;
    public static String DTFJIndexBuilder_NullTargetOfRoot;
    public static String DTFJIndexBuilder_ObjDescClass;
    public static String DTFJIndexBuilder_ObjDescObjType;
    public static String DTFJIndexBuilder_ObjDescObjTypeAddress;
    public static String DTFJIndexBuilder_ObjectIsFinalizable;
    public static String DTFJIndexBuilder_ObjectsFoundButClassesHadObjectsAndClassesInTotal;
    public static String DTFJIndexBuilder_Pass1;
    public static String DTFJIndexBuilder_Pass2;
    public static String DTFJIndexBuilder_PossibleProblemReadingJavaStackFrames;
    public static String DTFJIndexBuilder_PossibleProblemReadingJavaStackFramesLocation;
    public static String DTFJIndexBuilder_PossibleProblemReadingJavaStackFramesMethod;
    public static String DTFJIndexBuilder_PossibleProblemReadingNativeStackFrame;
    public static String DTFJIndexBuilder_PrimitiveShouldNotHaveASuperclass;
    public static String DTFJIndexBuilder_ProblemBuildingClassObject;
    public static String DTFJIndexBuilder_ProblemBuildingClassObjectForMethod;
    public static String DTFJIndexBuilder_ProblemCheckingBootLoaderReferences;
    public static String DTFJIndexBuilder_ProblemCheckingOutboundReferences;
    public static String DTFJIndexBuilder_ProblemCheckingOutboundReferencesForClass;
    public static String DTFJIndexBuilder_ProblemDetirminingFinalizeMethod;
    public static String DTFJIndexBuilder_ProblemDetirminingFinalizeMethodSig;
    public static String DTFJIndexBuilder_ProblemFindingClassesForObject;
    public static String DTFJIndexBuilder_ProblemFindingClassLoaderInformation;
    public static String DTFJIndexBuilder_ProblemFindingComponentClass;
    public static String DTFJIndexBuilder_ProblemFindingJavaLangClass;
    public static String DTFJIndexBuilder_ProblemFindingJavaLangClassViaName;
    public static String DTFJIndexBuilder_ProblemFindingRootInformation;
    public static String DTFJIndexBuilder_ProblemFindingThread;
    public static String DTFJIndexBuilder_ProblemGettingClassID;
    public static String DTFJIndexBuilder_ProblemGettingClassIDType;
    public static String DTFJIndexBuilder_ProblemGettingObjectClass;
    public static String DTFJIndexBuilder_ProblemGettingObjectSize;
    public static String DTFJIndexBuilder_ProblemGettingOutboundReferences;
    public static String DTFJIndexBuilder_ProblemGettingRoots;
    public static String DTFJIndexBuilder_ProblemGettingSizeOfJavaLangClass;
    public static String DTFJIndexBuilder_ProblemGettingSuperclass;
    public static String DTFJIndexBuilder_ProblemReadingArray;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackFrame;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackFrameLocation;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackFrames;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackFramesLocation;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackFramesMethod;
    public static String DTFJIndexBuilder_ProblemReadingJavaStackSection;
    public static String DTFJIndexBuilder_ProblemReadingJavaThreadInformation;
    public static String DTFJIndexBuilder_ProblemReadingJavaThreadInformationFor;
    public static String DTFJIndexBuilder_ProblemReadingJavaThreadName;
    public static String DTFJIndexBuilder_ProblemReadingNativeStackFrame;
    public static String DTFJIndexBuilder_ProblemReadingNativeStackSection;
    public static String DTFJIndexBuilder_ProblemReadingObjectFromField;
    public static String DTFJIndexBuilder_ProblemReadingThreadInformation;
    public static String DTFJIndexBuilder_ProcessingImageFromFile;
    public static String DTFJIndexBuilder_PurgedIdentifiers;
    public static String DTFJIndexBuilder_PurgingDeadObjectsFromImage;
    public static String DTFJIndexBuilder_RepeatedMessagesSuppressed;
    public static String DTFJIndexBuilder_SkippingObject;
    public static String DTFJIndexBuilder_SuperclassInWrongAddressSpace;
    public static String DTFJIndexBuilder_SuperclassNotFound;
    public static String DTFJIndexBuilder_ThreadNameNotFound;
    public static String DTFJIndexBuilder_ThreadObjectNotFound;
    public static String DTFJIndexBuilder_ThreadObjectNotFoundSoIgnoring;
    public static String DTFJIndexBuilder_ThreadStateNotFound;
    public static String DTFJIndexBuilder_TookmsToGetImageFromFile;
    public static String DTFJIndexBuilder_TookmsToParseFile;
    public static String DTFJIndexBuilder_UnableToFindClassLoader;
    public static String DTFJIndexBuilder_UnableToFindDTFJForFormat;
    public static String DTFJIndexBuilder_UnableToFindJavaRuntime;
    public static String DTFJIndexBuilder_UnableToFindJavaRuntimeId;
    public static String DTFJIndexBuilder_UnableToFindReachabilityOfRoot;
    public static String DTFJIndexBuilder_UnableToFindReferenceTypeOfRoot;
    public static String DTFJIndexBuilder_UnableToFindRoot;
    public static String DTFJIndexBuilder_UnableToFindSourceID;
    public static String DTFJIndexBuilder_UnableToFindSourceOfRoot;
    public static String DTFJIndexBuilder_UnableToFindTargetOfRoot;
    public static String DTFJIndexBuilder_UnableToFindThreadOwningMonitor;
    public static String DTFJIndexBuilder_UnableToFindTypeOfObject;
    public static String DTFJIndexBuilder_UnableToFindTypeOfRoot;
    public static String DTFJIndexBuilder_UnableToGetOutboundReference;
    public static String DTFJIndexBuilder_UnableToReadDumpInDTFJFormat;
    public static String DTFJIndexBuilder_UnableToReadDumpMetaInDTFJFormat;
    public static String DTFJIndexBuilder_UnexpectedBytecodeSectionSize;
    public static String DTFJIndexBuilder_UnexpectedCompiledCodeSectionSize;
    public static String DTFJIndexBuilder_UnexpectedModifiers;
    public static String DTFJIndexBuilder_UnexpectedNullReferenceTarget;
    public static String DTFJIndexBuilder_UnexpectedReferenceTargetType;
    public static String DTFJIndexBuilder_UnexpectedReferenceType;
    public static String DTFJIndexBuilder_UnexpectedValueForStaticField;
    public static String DTFJIndexBuilder_UnreferenceObjectsMarkedAsRoots;
    public static String DTFJIndexBuilder_UsingAddressForThread;
    public static String DTFJIndexBuilder_UsingAddressForThreadName;
    public static String DTFJIndexBuilder_UsingConservativeGarbageCollectionRoots;
    public static String DTFJIndexBuilder_UsingDTFJRoots;
    public static String DTFJIndexBuilder_UsingProcessPointerSizeNotAddressSpacePointerSize;
    public static String DTFJPreferencePage_AllMethods;
    public static String DTFJPreferencePage_Description;
    public static String DTFJPreferencePage_MethodsAsClasses;
    public static String DTFJPreferencePage_NoMethods;
    public static String DTFJPreferencePage_OnlyStackFrames;
    public static String DTFJPreferencePage_RunningMethods;
    public static String DTFJPreferencePage_RuntimeID;
    public static String StackFrameResolver_file;
    public static String StackFrameResolver_file_line;
    public static String StackFrameResolver_method;
    public static String StackFrameResolver_method_file;
    public static String StackFrameResolver_method_file_line;
    public static String ThreadDetailsResolver_alive;
    public static String ThreadDetailsResolver_blocked_on_monitor_enter;
    public static String ThreadDetailsResolver_DTFJ_Name;
    public static String ThreadDetailsResolver_in_native;
    public static String ThreadDetailsResolver_in_object_wait;
    public static String ThreadDetailsResolver_interrupted;
    public static String ThreadDetailsResolver_JNIEnv;
    public static String ThreadDetailsResolver_Native_id;
    public static String ThreadDetailsResolver_Native_stack;
    public static String ThreadDetailsResolver_parked;
    public static String ThreadDetailsResolver_Priority;
    public static String ThreadDetailsResolver_runnable;
    public static String ThreadDetailsResolver_sleeping;
    public static String ThreadDetailsResolver_suspended;
    public static String ThreadDetailsResolver_State;
    public static String ThreadDetailsResolver_State_value;
    public static String ThreadDetailsResolver_terminated;
    public static String ThreadDetailsResolver_vendor1;
    public static String ThreadDetailsResolver_vendor2;
    public static String ThreadDetailsResolver_vendor3;
    public static String ThreadDetailsResolver_waiting;
    public static String ThreadDetailsResolver_waiting_indefinitely;
    public static String ThreadDetailsResolver_waiting_with_timeout;
    static
    {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}
