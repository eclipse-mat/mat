/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.collect.IteratorLong;
import org.eclipse.mat.hprof.extension.IParsingEnhancer;
import org.eclipse.mat.hprof.internal.EnhancerRegistry;
import org.eclipse.mat.impl.snapshot.internal.SimpleMonitor;
import org.eclipse.mat.parser.IIndexBuilder;
import org.eclipse.mat.parser.IPreliminaryIndex;
import org.eclipse.mat.parser.index.IndexWriter;
import org.eclipse.mat.parser.index.IIndexReader.IOne2LongIndex;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.snapshot.SnapshotException;
import org.eclipse.mat.util.IProgressListener;


public class HprofIndexBuilder implements IIndexBuilder
{
    File file;
    String prefix;
    IOne2LongIndex id2position;
    List<IParsingEnhancer> enhancers;

    public void init(File file, String prefix)
    {
        this.file = file;
        this.prefix = prefix;

        this.enhancers = new ArrayList<IParsingEnhancer>();
        for (EnhancerRegistry.Enhancer enhancer : EnhancerRegistry.instance().delegates())
        {
            IParsingEnhancer parsingEnhancer = enhancer.parser();
            if (parsingEnhancer != null)
                this.enhancers.add(parsingEnhancer);
        }

    }

    public void fill(IPreliminaryIndex preliminary, IProgressListener listener) throws SnapshotException, IOException
    {
        IHprofParserHandler handler = null;
        PositionInputStream in = null;

        try
        {
            SimpleMonitor monitor = new SimpleMonitor(MessageFormat.format("Parsing {0}", new Object[] { file
                            .getAbsolutePath() }), listener, new int[] { 500, 1500 });

            listener.beginTask(MessageFormat.format("Parsing {0}", file.getName()), 3000);

            in = new PositionInputStream(new BufferedInputStream(new FileInputStream(file)));
            int magicNumber = in.readInt();
            if (magicNumber == HprofBasics.MAGIC_NUMBER)
            {
                handler = new HprofParserHandlerImpl();
                handler.beforePass1(preliminary.getSnapshotInfo());

                for (IParsingEnhancer enhancer : enhancers)
                    enhancer.beforePass1(handler);

                try
                {
                    SimpleMonitor.Listener mon = (SimpleMonitor.Listener) monitor.nextMonitor();
                    mon.beginTask(MessageFormat.format("Scanning {0}", new Object[] { file.getAbsolutePath() }),
                                    (int) (file.length() / 1000));
                    Pass1Parser parser = new Pass1Parser(in, 1, handler, mon);
                    parser.read();

                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();

                    mon.done();
                }
                finally
                {
                    try
                    {
                        in.close();
                        in = null;
                    }
                    catch (IOException ignore)
                    {
                        // $JL-EXC$
                    }
                }

                handler.beforePass2(listener);
                for (IParsingEnhancer enhancer : enhancers)
                    enhancer.beforePass2(handler);

                try
                {
                    in = new PositionInputStream(new BufferedInputStream(new FileInputStream(file)));
                    in.readInt();

                    SimpleMonitor.Listener mon = (SimpleMonitor.Listener) monitor.nextMonitor();
                    mon.beginTask(MessageFormat.format("Extracting objects from {0}", new Object[] { file
                                    .getAbsolutePath() }), (int) (file.length() / 1000));
                    Pass2Parser parser = new Pass2Parser(in, 1, handler, mon);
                    parser.read();

                    if (listener.isCanceled())
                        throw new IProgressListener.OperationCanceledException();

                    mon.done();
                }
                finally
                {
                    try
                    {
                        if (in != null)
                            in.close();
                        in = null;
                    }
                    catch (IOException ignore)
                    {
                        // $JL-EXC$
                    }
                }

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();

                for (IParsingEnhancer enhancer : enhancers)
                    enhancer.beforeCompletion(handler);

                id2position = handler.fillIn(preliminary);
            }
            else
            {
                throw new SnapshotException(MessageFormat.format("Unknown magic number in HPROF: {0}", magicNumber));
            }
        }
        finally
        {
            try
            {
                if (in != null)
                    in.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
        }
    }

    public void clean(final int[] purgedMapping, IProgressListener listener) throws IOException
    {

        // //////////////////////////////////////////////////////////////
        // object 2 hprof position
        // //////////////////////////////////////////////////////////////

        File indexFile = new File(prefix + "o2hprof.index");
        listener.subTask(MessageFormat.format("Writing {0}", new Object[] { indexFile.getAbsolutePath() }));
        IOne2LongIndex newIndex = new IndexWriter.LongIndexStreamer().writeTo(indexFile, new IndexIterator(id2position,
                        purgedMapping));

        try
        {
            newIndex.close();
        }
        catch (IOException ignore)
        {
            // $JL-EXC$
        }
        try
        {
            id2position.close();
        }
        catch (IOException ignore)
        {
            // $JL-EXC$
        }
        id2position.delete();
        id2position = null;
    }

    public void cancel()
    {
        if (id2position != null)
        {
            try
            {
                id2position.close();
            }
            catch (IOException ignore)
            {
                // $JL-EXC$
            }
            id2position.delete();
        }
    }

    private static final class IndexIterator implements IteratorLong
    {
        private final IOne2LongIndex id2position;
        private final int[] purgedMapping;
        private int nextIndex = -1;

        private IndexIterator(IOne2LongIndex id2position, int[] purgedMapping)
        {
            this.id2position = id2position;
            this.purgedMapping = purgedMapping;
            findNext();
        }

        public boolean hasNext()
        {
            return nextIndex < purgedMapping.length;
        }

        public long next()
        {
            long answer = id2position.get(nextIndex);
            findNext();
            return answer;
        }

        protected void findNext()
        {
            nextIndex++;
            while (nextIndex < purgedMapping.length && purgedMapping[nextIndex] < 0)
                nextIndex++;
        }
    }
}
