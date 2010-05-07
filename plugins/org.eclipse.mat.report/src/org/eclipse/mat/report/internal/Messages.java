/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import org.eclipse.osgi.util.NLS;

public final class Messages extends NLS
{
    private static final String BUNDLE_NAME = "org.eclipse.mat.report.internal.messages"; //$NON-NLS-1$

    public static String ArgumentSet_Error_IllegalArgument;
    public static String ArgumentSet_Error_Inaccessible;
    public static String ArgumentSet_Error_Instantiation;
    public static String ArgumentSet_Error_MissingMandatoryArgument;
    public static String ArgumentSet_Error_NoSuchArgument;
    public static String ArgumentSet_Error_SetField;
    public static String ArgumentSet_Msg_NullValue;
    public static String CategoryDescriptor_Label_NoCategory;
    public static String CommandLine_Error_AssignmentFailed;
    public static String CommandLine_Error_InvalidCommand;
    public static String CommandLine_Error_MissingArgument;
    public static String CommandLine_Error_MissingValue;
    public static String CommandLine_Error_NotFound;
    public static String CommandLine_Error_NoUnflaggedArguments;
    public static String ConsoleProgressListener_Label_Subtask;
    public static String ConsoleProgressListener_Label_Task;
    public static String ContextDerivedData_Error_OperationNotFound;
    public static String Converters_Error_InvalidEnumValue;
    public static String DisplayFileResult_Label_NoFile;
    public static String Filter_Error_IllegalCharacters;
    public static String Filter_Error_InvalidRegex;
    public static String Filter_Error_Parsing;
    public static String Filter_Label_Numeric;
    public static String Filter_Label_Regex;
    public static String HtmlOutputter_Error_MovingFile;
    public static String HtmlOutputter_Label_Details;
    public static String HtmlOutputter_Label_NotApplicable;
    public static String HtmlOutputter_Msg_TreeIsLimited;
    public static String PageSnippets_Label_HideUnhide;
    public static String PageSnippets_Label_CreatedBy;
    public static String PageSnippets_Label_OpenInMemoryAnalyzer;
    public static String PageSnippets_Label_StartPage;
    public static String PageSnippets_Label_TableOfContents;
    public static String PartsFactory_Error_Construction;
    public static String PropertyResult_Column_Name;
    public static String PropertyResult_Column_Value;
    public static String Quantize_Error_MismatchArgumentsColumns;
    public static String Queries_Error_NotAvialable;
    public static String Queries_Error_UnknownArgument;
    public static String QueryDescriptor_Error_IgnoringQuery;
    public static String QueryDescriptor_Error_NotSupported;
    public static String QueryPart_Error_ColumnNotFound;
    public static String QueryPart_Error_Filter;
    public static String QueryPart_Error_IgnoringResult;
    public static String QueryPart_Error_InvalidProvider;
    public static String QueryPart_Error_InvalidProviderOperation;
    public static String QueryPart_Error_MissingEqualsSign;
    public static String QueryPart_Error_NoCommand;
    public static String QueryPart_Error_RetainedSizeColumnNotFound;
    public static String QueryPart_Error_SortColumnNotFound;
    public static String QueryPart_Label_ReportRoot;
    public static String QueryPart_Msg_TestProgress;
    public static String QueryRegistry_Error_Advice;
    public static String QueryRegistry_Error_Argument;
    public static String QueryRegistry_Error_Inaccessible;
    public static String QueryRegistry_Error_NameBound;
    public static String QueryRegistry_Error_Registering;
    public static String QueryRegistry_MissingLabel;

    public static String QueryRegistry_Msg_QueryRegistered;
    public static String QuerySpec_Error_IncompatibleTypes;

    public static String QueueInt_ZeroSizeQueue;
    public static String RefinedResultBuilder_Error_ColumnsSorting;
    public static String RefinedResultBuilder_Error_UnsupportedType;
    public static String RegistryReader_Error_Registry;
    public static String RendererRegistry_Error_MissingAnnotation;
    public static String ReportPlugin_InternalError;
    public static String ResultRenderer_Error_OutputterNotFound;
    public static String ResultRenderer_Label_Details;
    public static String ResultRenderer_Label_TableOfContents;
    public static String RunRegisterdReport_Error_UnknownReport;
    public static String SpecFactory_Error_MissingTemplate;
    public static String TextResult_Label_Links;
    public static String TotalsRow_Label_Filtered;
    public static String TotalsRow_Label_Total;
    public static String TotalsRow_Label_TotalVisible;

	public static String ConsoleProgressListener_ERROR;
	public static String ConsoleProgressListener_INFO;
	public static String ConsoleProgressListener_UNKNOWN;
	public static String ConsoleProgressListener_WARNING;

	static
    {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages()
    {}
}
