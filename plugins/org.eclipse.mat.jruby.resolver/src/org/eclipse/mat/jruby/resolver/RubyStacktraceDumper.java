/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dimitar Giormov - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.jruby.resolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.osgi.util.NLS;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.internal.runtime.RubyRunnable;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.ThreadContext.RubyStackTraceElement;

/**
 * Utilities for extracting Ruby stack traces from Java processes.
 * 
 * @author Dimitar Giormov
 */
@Subject("org.jruby.internal.runtime.RubyRunnable")
public class RubyStacktraceDumper implements IRequestDetailsResolver {

	public void complement(ISnapshot snapshot, IThreadInfo thread, int[] javaLocals, int thisJavaLocal, IProgressListener listener) throws SnapshotException {
        // Instantiate a Ruby runtime so we can reuse the logic for printing stacktraces
        // (which is very non-trivial!)
        // TODO: Make this available from Ruby so we can reuse a runtime.
        // This would also enable hot-linking the stacktrace from within Eclipse,
        // which only understands the stack trace output if the process is launched 
        // as a Ruby target rather than Java.
		Ruby ruby = null;
		try {
			ruby = Ruby.newInstance();
		} catch (NoClassDefFoundError e) {
			thread.addRequest(
					Messages.RubyStacktraceDumper_NoJRuby, 
					new TextResult(Messages.RubyStacktraceDumper_NoJRubyDetails, true));
			return;
		}
        
        for (Entry<String, RubyStackTraceElement[]> entry : getAllStackTraces(snapshot).entrySet()) {
            String javaThreadName = entry.getKey();
            RubyStackTraceElement[] backtraceFrames = entry.getValue();
            CompositeResult result = new CompositeResult();
            
            RubyArray backtrace = (RubyArray) ThreadContext.createBacktraceFromFrames(ruby, backtraceFrames);
            if (javaThreadName == null && backtrace.size() == 1 && "<script>:1".equals(((String) backtrace.get(0)).trim())) {
            	continue;
            }
            if(!thread.getThreadObject().getClassSpecificName().equals(javaThreadName)){
            	continue;
            }
            
            StringBuilder stackTrace = new StringBuilder();
            for (Object element : backtrace){
                stackTrace.append(NLS.bind(Messages.RubyStacktraceDumper_StackTraceLine, element));
            }

            String summary = NLS.bind(Messages.RubyStacktraceDumper_Summary, javaThreadName);
            
            if (javaThreadName == null) {
            	javaThreadName = Messages.RubyStacktraceDumper_UnknownThread;
            }
            
            result.addResult(
            		NLS.bind(Messages.RubyStacktraceDumper_ResultHeader, javaThreadName), 
            		new TextResult(stackTrace.toString(), true));
            thread.addRequest(summary, result);
            thread.addKeyword("thread_details");
        }
    }
    
    /**
     * Extracts the Ruby stack trace for all active Ruby threads in the given heap dump.
     * @throws SnapshotException 
     */
    public static Map<String, RubyStackTraceElement[]> getAllStackTraces(final ISnapshot model) throws SnapshotException {
        // Build a mapping between RubyThreads and Java Thread names.
        // The key is the RubyRunnable class, which is used in some way for any new Ruby thread.
        // TODO: This doesn't cover the root/main Ruby thread, which adopts the current Java thread!
        // There may be other cases I'm not covering as well.
        final Map<RubyThreadProxy, String> javaThreadNameForRubyThread = new HashMap<RubyThreadProxy, String>();
//        final JavaClass rubyRunnableClass = model.findClass(RubyRunnable.class.getName());
        Collection<IClass> classesByName = model.getClassesByName(RubyRunnable.class.getName(), false);
        for (IClass rubyRunnableClass : classesByName) {
        	int[] objectIds = rubyRunnableClass.getObjectIds();
        	for (int id : objectIds) {
        		IObject runnable = model.getObject(id);
        		final RubyRunnableProxy runnableProxy = HeapDumpProxy.make(RubyRunnableProxy.class, RubyRunnable.class, runnable);
        		final RubyThreadProxy rubyThread = runnableProxy.rubyThread();
        		final ThreadProxy thread = runnableProxy.javaThread();
                
                // javaThread isn't set until RubyRunnable#run() is called, so it might be null.
                if (thread != null) {
                    final String threadName = new String(thread.name());
                    
                    javaThreadNameForRubyThread.put(rubyThread, threadName);
                }
			}
		}
        
        // Now build up the actual stack traces. All the state we need is in ThreadContext instances.
        final Map<String, RubyStackTraceElement[]> stackTraces = new HashMap<String, RubyStackTraceElement[]>();
        Collection<IClass> classesByName2 = model.getClassesByName(ThreadContext.class.getName(), false);
        for (IClass threadContextClass : classesByName2) {
        	int[] objectIds = threadContextClass.getObjectIds();
        	for (int id : objectIds) {
        		IObject threadContext = model.getObject(id);
        		final ThreadContextProxy threadContextProxy = HeapDumpProxy.make(ThreadContextProxy.class, ThreadContext.class, threadContext);

        		final RubyThreadProxy rubyThread = threadContextProxy.thread();
        		final String javaThreadName = javaThreadNameForRubyThread.get(rubyThread);

        		final String currentFile = threadContextProxy.file();
        		final int currentLine = threadContextProxy.line();

        		final FrameProxy[] frames = threadContextProxy.frameStack();
        		final int frameIndex = threadContextProxy.frameIndex();
        		// + 1 for the fact that frameIndex is the index of the top of the stack, 
        		// + 1 for the extra frame to include the current file/line number.
        		final int traceSize = frameIndex + 2;
        		final RubyStackTraceElement[] backtraceFrames = new RubyStackTraceElement[traceSize];

        		// Fill in the extra stack frame for the current location.
        		// The class/method names won't be used - they come from the next frame up.
        		backtraceFrames[0] = new RubyStackTraceElement(
        				Messages.RubyStacktraceDumper_Unknown, Messages.RubyStacktraceDumper_Unknown, 
        				currentFile, currentLine + 1, false);

        		// This is pretty much identical to ThreadContext#buildTrace(RubyStackTraceElement[])
        		for (int i = 0; i <= frameIndex; i++) {
        			final FrameProxy frame = frames[i];

        			final String frameFileName = frame.fileName();
        			final int frameLine = frame.line();
        			final String methodName = getMethodNameFromFrame(frame);
        			final String moduleName = getClassNameFromFrame(frame);
        			final boolean isBindingFrame = frame.isBindingFrame();

        			backtraceFrames[traceSize - 1 - i] = new RubyStackTraceElement(moduleName, methodName, frameFileName, frameLine + 1, isBindingFrame);
        		}

        		stackTraces.put(javaThreadName, backtraceFrames);
			}
        }

        return stackTraces;
    }
    
    /**
     * Extract the class name for a frame (proxy). We need a non-null String or else the
     * {@link StackTraceElement} constructor will complain. 
     * <p>
     * Derived from ThreadContext version,
     * with the added wrinkle that the klazz field is lazily filled in and hence might
     * be <code>null</code>.
     * @param current
     */
    private static String getClassNameFromFrame(FrameProxy current) {
        String klazzName = Messages.RubyStacktraceDumper_Unknown;
        final RubyModuleProxy klazz = current.klazz();
        if (klazz != null) {
            final String fullName = klazz.fullName();
            if (fullName != null) {
                klazzName = fullName;
            }
        }
        return klazzName;
    }
    
    /**
     * Extract the method name for a frame (proxy). We need a non-null String or else the
     * {@link StackTraceElement} constructor will complain. 
     * <p>
     * Derived from ThreadContext version.
     * @param current
     */
    private static String getMethodNameFromFrame(FrameProxy current) {
        String methodName = current.name();
        if (current.name() == null) {
            methodName = Messages.RubyStacktraceDumper_Unknown;
        }
        return methodName;
    }
    
    /**
     * Proxy classes for use with {@link HeapDumpProxy}. 
     */
    
    public static interface ThreadContextProxy {
        RubyThreadProxy thread();
        
        FrameProxy[] frameStack();
        int frameIndex();
        
        String file();
        int line();
    }
    
    public static interface RubyRunnableProxy {
        RubyThreadProxy rubyThread();
        ThreadProxy javaThread();
    }
    
    public static interface RubyThreadProxy {}
    
    public static interface ThreadProxy {
        char[] name();
    }
    
    public static interface FrameProxy {
        String fileName();
        int line();
        String name();
        RubyModuleProxy klazz();
        boolean isBindingFrame();
    }
    
    public static interface RubyModuleProxy {
        String fullName();
    }
    
}
