/*******************************************************************************
 * Copyright (c) 2008, 2014 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractor;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.collectionextract.FieldSizeArrayCollectionExtractor;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.HelpUrl;
import org.eclipse.mat.query.quantize.Quantize;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.util.IProgressListener;

@CommandName("collection_fill_ratio")
@HelpUrl("/org.eclipse.mat.ui.help/tasks/analyzingjavacollectionusage.html")
public class CollectionFillRatioQuery extends AbstractFillRatioQuery implements IQuery
{

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public int segments = 5;

    @Argument(isMandatory = false)
    public String collection;

    @Argument(isMandatory = false)
    public String size_attribute;

    @Argument(isMandatory = false)
    public String array_attribute;

    public IResult execute(IProgressListener listener) throws Exception
    {
        listener.subTask(Messages.CollectionFillRatioQuery_ExtractingFillRatios);

        // create frequency distribution
        // The load factor should be <= 1, but for old PHD files with inaccurate array sizes can appear > 1.
        // Therefore we have a larger upper bound of 5, not 1 just in case
        // Using 5.0 and Quantize counting back from 5.0 using the reciprocal always seems to give 1.000 or 1.000+ 
        Quantize.Builder builder = Quantize.linearFrequencyDistribution(
                        Messages.CollectionFillRatioQuery_Column_FillRatio, 0, 5.0000000000, (double) 1 / (double) segments);
        builder.column(Messages.CollectionFillRatioQuery_ColumnNumObjects, Quantize.COUNT);
        builder.column(Messages.Column_ShallowHeap, Quantize.SUM_LONG);
        builder.addDerivedData(RetainedSizeDerivedData.APPROXIMATE);
        Quantize quantize = builder.build();


        CollectionExtractor specificExtractor;
        if (size_attribute != null && array_attribute != null) {
            specificExtractor = new FieldSizeArrayCollectionExtractor(size_attribute, array_attribute);
        } else if (size_attribute == null && array_attribute == null) {
            specificExtractor = null;
        } else {
            throw new IllegalArgumentException("need both or none of size and array attributes");
        }

        runQuantizer(listener, quantize, specificExtractor, collection, snapshot, objects);
        return quantize.getResult();
    }
}
