/*******************************************************************************
* Copyright (c) 2012 Filippo Pacifici
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
* Filippo Pacifici - initial API and implementation
*******************************************************************************/
package org.eclipse.mat.ui.snapshot.panes.oql.textPartitioning;

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

/**
 * Partitioning rule that finds a string included between two sequences
 * The beginning and trailing sequences are not case sensitive
 * The trailing sequence is not included and the character scanner is
 * rewinded once the trailer is found.
 * 
 * @author Filippo Pacifici
 *
 */
public class MultilineNonConsumingRule implements IRule, IPredicateRule {

	/**
	 * Rule starting sequence.
	 */
	String startSequence;
	
	/**
	 * Rule ending sequence
	 */
	String endSequences[];
	
	/**
	 * Toekn to be returned
	 */
	IToken token;
	
	/**
	 * Base constructor to specify start sequence, end sequence and token.
	 * @param startSequence
	 * @param endSequence
	 * @param token
	 */
	public MultilineNonConsumingRule(String startSequence, String endSequences[],
			IToken token) {
		super();
		this.startSequence = startSequence;
		this.endSequences = endSequences;
		this.token = token;
	}

	/**
	 * {@inheritDoc}
	 * Searches the token in the sequence. This method can be called from the middle of 
	 * the sequence (passing arg1 
	 */
	public IToken evaluate(ICharacterScanner arg0, boolean resume) {
		if (!resume){
			return evaluate(arg0);
		}else {
			findEndToken(arg0, endSequences);
			return token;
		}
	}

	public IToken getSuccessToken() {
		return token;
	}

	/**
	 * {@inheritDoc}
	 * Evaluates if the provided character scanner starts with the startSequence and terminates
	 * with endSequence, then it rewinds.
	 */
	public IToken evaluate(ICharacterScanner arg0) {
		if (startsWith(arg0, startSequence)){
			findEndToken(arg0, endSequences);
			return token;
		}else {
			return new BadToken();
		}
	}
	
	/**
	 * Dummy token used to specify in return that a token has not been found.
	 * @author Filippo Pacifici
	 *
	 */
	public static class BadToken implements IToken {

		public Object getData() {
			return null;
		}

		public boolean isEOF() {
			return false;
		}

		public boolean isOther() {
			return false;
		}

		public boolean isUndefined() {
			return true;
		}

		public boolean isWhitespace() {
			return false;
		}
		
	}

	/**
	 * Returns true if the sequence starts with beginningSequence.
	 * @param sequence
	 * @param beginningSequence
	 * @return
	 */
	private boolean startsWith(ICharacterScanner sequence, String beginningSequence){
		char[] seqRead = new char[beginningSequence.length()];
		
		int read = sequence.read();
		int numRead = 1;
		
		for (int offset = 0 ; offset < beginningSequence.length() && read != ICharacterScanner.EOF ; offset ++){
			seqRead[offset] = (char) read;
			read = sequence.read();
			numRead++;
		}
			
		String strRead = new String(seqRead);
		if (strRead.equalsIgnoreCase(beginningSequence)){
			return true;
		}else{
			//rewind
			rewind(sequence, numRead);
			return false;
		}
	}
	
	/**
	 * Scans the sequence up to the end token or to the end of the sequence
	 * @param sequence
	 * @param endSequence
	 * @return
	 */
	private boolean findEndToken(ICharacterScanner sequence, String endSequences[]){
		char[][] endSeq = new char[endSequences.length][];
		int endSeqOffset[] = new int[endSequences.length];
		
		for (int i = 0 ; i < endSequences.length ; i ++){
			endSeq[i] = endSequences[i].toCharArray();
		}
		
		int currentC = sequence.read();
		int sequenceFound = -1;
		while (currentC != ICharacterScanner.EOF && sequenceFound == -1){
			sequenceFound = performStep((char)currentC,endSeqOffset,endSeq);
			currentC = sequence.read();
		}
		
		//decides if to rewind
		if (sequenceFound != -1){
			rewind(sequence, endSequences[sequenceFound].length() +1);
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * Checks if the passed character corresponds to the expected character for each of 
	 * the end sequences.
	 * If it is the case it increases the offsets
	 * @param sequence
	 * @param offsets
	 * @param endSeq
	 * @return the index of the completed string if any, otherwise -1
	 */
	private int performStep(char character, int[] offsets, char[][] endSeq){
		for (int i = 0 ; i < offsets.length ; i ++){
			if (Character.toUpperCase(character) == Character.toUpperCase(endSeq[i][offsets[i]])){
				offsets[i] ++;
				if (offsets[i] == endSeq[i].length){
					return i;
				}
			}
		}
		return -1;
	}
	
	/**
	 * Reqinds the sequence to remove the end sequence.
	 * @param sequence
	 * @param endSequence
	 */
	private void rewind(ICharacterScanner sequence, int length){
		//using <= since I have to rewind also the last read I did
		for (int i = 0 ; i < length ; i++){
			sequence.unread();
		}
	}
}
