/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dimitar Giormov - initial API and implementation
 *    Krum Tsvetkov - cleanup jruby dependency
 *******************************************************************************/
package org.eclipse.mat.jruby.resolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IRequestDetailsResolver;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.osgi.util.NLS;

/**
 * Utilities for extracting Ruby stack traces from Java processes.
 * 
 * @author Dimitar Giormov
 */
@Subjects({"java.lang.Thread", "org.jruby.Main"})
public class RubyStacktraceDumper implements IRequestDetailsResolver {
	
	public final static String RUBY_RUNNABLE_CLASS = "org.jruby.internal.runtime.RubyRunnable";
	public final static String THREAD_CONTEXT_CLASS = "org.jruby.runtime.ThreadContext";

	public void complement(ISnapshot snapshot, IThreadInfo thread, int[] javaLocals, int thisJavaLocal, IProgressListener listener) throws SnapshotException {
        
		if (snapshot.getClassesByName(THREAD_CONTEXT_CLASS, false) == null){
			return;
		}
		Map<String, FrameModel[]> allStackTraces = getAllStackTraces(snapshot);
        for (Entry<String, FrameModel[]> entry : allStackTraces.entrySet()) {
            String javaThreadName = entry.getKey();
            FrameModel[] backtraceFrames = entry.getValue();
            CompositeResult result = new CompositeResult();
            
            if(!thread.getThreadObject().getClassSpecificName().equals(javaThreadName)){
            	continue;
            }
            
            PrintableStackFrame[] stackTraceFrames = buildPrintableStack(backtraceFrames);
            
            StringBuilder stackTrace = new StringBuilder();
            for (PrintableStackFrame element : stackTraceFrames){
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
    
	
	private IObject findIncomingThreadRef(ISnapshot model, IObject threadContext) throws SnapshotException{
		Collection<IClass> classesByName = model.getClassesByName("java.lang.Thread", false);
    	for (IClass javaThreads : classesByName) {
    		int[] objectIds = javaThreads.getObjectIds();
    		for (int id : objectIds) {
    			IObject jThread = model.getObject(id);
    			List<NamedReference> outboundReferences = jThread.getOutboundReferences();
    			for (NamedReference namedReference : outboundReferences) {
    				IObject object = model.getObject(namedReference.getObjectId());
    				if(object.getTechnicalName().startsWith(THREAD_CONTEXT_CLASS)){
    					if (object.getObjectId() == threadContext.getObjectId()){
    						return jThread;
    					}
    				}
				}
    		}
    	}
    	return null;
	}
    /**
     * Extracts the Ruby stack trace for all active Ruby threads in the given heap dump.
     * @throws SnapshotException 
     */
    public Map<String, FrameModel[]> getAllStackTraces(final ISnapshot model) throws SnapshotException {
        final Map<IObject, String> javaThreadNameForRubyThread = new HashMap<IObject, String>();
        gatherThreadMap(model, javaThreadNameForRubyThread);
        
        // Now build up the actual stack traces. All the state we need is in ThreadContext instances.
        final Map<String, FrameModel[]> stackTraces = new HashMap<String, FrameModel[]>();
        Collection<IClass> classesByName2 = model.getClassesByName(THREAD_CONTEXT_CLASS, false);
        //If the thread name cannot be found a default name with index is assigned.
        int tempIndex = 0;
        for (IClass threadContextClass : classesByName2) {
        	int[] objectIds = threadContextClass.getObjectIds();
        	for (int id : objectIds) {
        		IObject threadContext = model.getObject(id);

        		IObject rubyThread = (IObject) threadContext.resolveValue("thread");
        		String javaThreadName = javaThreadNameForRubyThread.get(rubyThread); // TODO check if this works

        		final String currentFile = ((IObject) threadContext.resolveValue("file")).getClassSpecificName();
        		final int currentLine = (Integer) threadContext.resolveValue("line");

        		IObjectArray frames = (IObjectArray) threadContext.resolveValue("frameStack");
        		final int frameIndex = (Integer) threadContext.resolveValue("frameIndex");
        		// + 1 for the fact that frameIndex is the index of the top of the stack, 
        		// + 1 for the extra frame to include the current file/line number.
        		final int traceSize = frameIndex + 2;
        		final FrameModel[] backtraceFrames = new FrameModel[traceSize];

        		// Fill in the extra stack frame for the current location.
        		// The class/method names won't be used - they come from the next frame up.
        		backtraceFrames[0] = new FrameModel(
        				Messages.RubyStacktraceDumper_Unknown, Messages.RubyStacktraceDumper_Unknown, 
        				currentFile, currentLine + 1, false);

        		// This is pretty much identical to ThreadContext#buildTrace(RubyStackTraceElement[])
        		long[] addresses = frames.getReferenceArray();
        		for (int i = 0; i <= frameIndex; i++) {
        			IObject frameObject = model.getObject(model.mapAddressToId(addresses[i]));
        			FrameModel frame = new FrameModel(frameObject);
        			backtraceFrames[traceSize - 1 - i] = frame;
        		}

        		if (javaThreadName == null){
        			javaThreadName = "Unknown Thread" + tempIndex++;
        		}
        		stackTraces.put(javaThreadName, backtraceFrames);
			}
        }

        return stackTraces;
    }

	protected void gatherThreadMap(final ISnapshot model,
			final Map<IObject, String> javaThreadNameForRubyThread)
			throws SnapshotException {
		if (model.getClassesByName(RUBY_RUNNABLE_CLASS, false) != null) {
        	Collection<IClass> classesByName = model.getClassesByName(RUBY_RUNNABLE_CLASS, false);
        	for (IClass rubyRunnableClass : classesByName) {
        		int[] objectIds = rubyRunnableClass.getObjectIds();
        		for (int id : objectIds) {
        			IObject runnable = model.getObject(id);
        			IObject rubyThread = (IObject) runnable.resolveValue("rubyThread");
        			IObject thread = (IObject) runnable.resolveValue("javaThread");

        			// javaThread isn't set until RubyRunnable#run() is called, so it might be null.
        			if (thread != null) {
        				final String threadName = new String(thread.getClassSpecificName());

        				javaThreadNameForRubyThread.put(rubyThread, threadName);
        			}
        		}
        	}
        } 
		if (model.getClassesByName(THREAD_CONTEXT_CLASS, false) != null) {
        	Collection<IClass> classesByName = model.getClassesByName(THREAD_CONTEXT_CLASS, false);
        	for (IClass threadContextClass : classesByName) {
        		int[] objectIds = threadContextClass.getObjectIds();
        		for (int id : objectIds) {
        			IObject tctxt = model.getObject(id);
        			IObject thread = findIncomingThreadRef(model, tctxt);
        			// javaThread isn't set until RubyRunnable#run() is called, so it might be null.
        			if (thread != null) {
        				final String threadName = new String(thread.getClassSpecificName());
        				javaThreadNameForRubyThread.put(tctxt, threadName);
        			}
        		}
        	}
        }
//		else {
        	Collection<IClass> classesByName = model.getClassesByName("java.lang.Thread", false);
        	for (IClass javaThreads : classesByName) {
        		int[] objectIds = javaThreads.getObjectIds();
        		IObject rubyThread = null;
        		IObject thread = null;
        		for (int id : objectIds) {
        			IObject jThread = model.getObject(id);
        			List<NamedReference> outboundReferences = jThread.getOutboundReferences();
        			rubyThread = null;
        			for (NamedReference namedReference : outboundReferences) {
        				IObject object = model.getObject(namedReference.getObjectId());
        				if(object.getTechnicalName().startsWith(THREAD_CONTEXT_CLASS)){
        					rubyThread = (IObject) object.resolveValue("thread");
        					thread = jThread;
        					break;
        				}
					}
        			

        			// javaThread isn't set until RubyRunnable#run() is called, so it might be null.
        			if (thread != null && rubyThread != null) {
        				final String threadName = new String(thread.getClassSpecificName() != null ? thread.getClassSpecificName() : thread.getTechnicalName());
        				javaThreadNameForRubyThread.put(rubyThread, threadName);
        			}
        		}
        	}
//        }
	}
    
    private static String getMethodNameFromFrame(FrameModel current) {
        String methodName = current.name();
        if (current.name() == null) {
            methodName = Messages.RubyStacktraceDumper_Unknown;
        }
        return methodName;
    }
    
    private static PrintableStackFrame[] buildPrintableStack(FrameModel[] frames)
    {
    	PrintableStackFrame[] result = new PrintableStackFrame[frames.length - 1];
    	for (int i = 0; i < result.length; i++)
    	{
    		int line = frames[i].line;
    		if (i > 0) line += 1; // TODO not sure why +1?
    		// take the method name from the next frame
    		String methodName = getMethodNameFromFrame(frames[i + 1]);
    		result[i] = new PrintableStackFrame(frames[i].fileName, line, methodName);
    	}
    	
    	return result;
    }
    
    public static class FrameModel {
    	
		String fileName;
        int line;
        String name;
        String moduleName;
        boolean isBindingFrame;
    	
        public FrameModel(IObject object) throws SnapshotException
		{
			super();
			fileName = ((IObject) object.resolveValue("fileName")).getClassSpecificName();
			line = (Integer) object.resolveValue("line");
			IObject nameObject = (IObject) object.resolveValue("name");
			if (nameObject != null)
				name = nameObject.getClassSpecificName();
			IObject klazz = (IObject) object.resolveValue("klazz");
			moduleName = getClassNameFromFrame(klazz);
			isBindingFrame = (Boolean) object.resolveValue("isBindingFrame");
		}
        
        public FrameModel(String moduleName, String methodName, String frameFileName, int frameLine, boolean isBindingFrame) throws SnapshotException
		{
			this.fileName = frameFileName;
			this.line = frameLine;
			this.name = methodName;
			this.moduleName = moduleName;
			this.isBindingFrame = isBindingFrame;
		}
        
        private String getClassNameFromFrame(IObject klazz) throws SnapshotException {
            String klazzName = Messages.RubyStacktraceDumper_Unknown;
            String fullName = null;
            if (klazz != null) {
            	IObject fullNameObject = (IObject) klazz.resolveValue("fullName");
            	if (fullNameObject != null)
            		fullName = fullNameObject.getClassSpecificName();
                if (fullName != null && !"".equals(fullName.trim())) {
                    klazzName = fullName;
                }
            }
            return klazzName;
        }        
        
		String fileName()
		{
			return fileName;
		}
		
        int line()
        {
        	return line;
        }
        
        String name()
        {
        	return name;
        }
        
        String moduleName()
        {
        	return moduleName;
        }
        
        boolean isBindingFrame()
        {
        	return isBindingFrame;
        }
        
        @Override
        public String toString()
        {
        	return fileName + ":" + line + " in " + getMethodNameFromFrame(this);
        }
    }
    
    private static class PrintableStackFrame
    {
    	String fileName;
    	int line;
    	String methodName;

    	public PrintableStackFrame(String fileName, int line, String methodName)
		{
			super();
			this.fileName = fileName;
			this.line = line;
			this.methodName = methodName;
		}
		public String getFileName()
		{
			return fileName;
		}
		public int getLine()
		{
			return line;
		}
		public String getMethodName()
		{
			return methodName;
		}
        
        @Override
        public String toString()
        {
        	return new StringBuilder(fileName).append(':').append(line).append(" in '").append(methodName).append('\'').toString();
        }
        
    }
    
}
