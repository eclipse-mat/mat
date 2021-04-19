/* 
 * DEFLATE library (Java)
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/deflate-library-java
 * https://github.com/nayuki/DEFLATE-library-Java
 * Modified for Eclipse Memory Analyzer for:
 *  cloning of inflator
 *  pass-through mode for multi-chunk zips
 */

package io.nayuki.deflate;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;


/**
 * Decompresses a DEFLATE data stream (raw format without zlib or gzip headers or footers) into a byte stream.
 */
public final class InflaterInputStream extends FilterInputStream {
    
    /*---- Fields ----*/
    
    /* Data buffers */
    
    // Buffer of bytes read from in.read() (the underlying input stream)
    private byte[] inputBuffer;     // Can have any positive length (but longer means less overhead)
    private int inputBufferLength;  // Number of valid prefix bytes, at least 0
    private int inputBufferIndex;   // Index of next byte to consume
    
    // Buffer of bits packed from the bytes in 'inputBuffer'
    private long inputBitBuffer;       // 0 <= value < 2^inputBitBufferLength
    private int inputBitBufferLength;  // Always in the range [0, 63]
    
    // Queued bytes to yield first when this.read() is called
    private byte[] outputBuffer;     // Should have length 257 (but pointless if longer)
    private int outputBufferLength;  // Number of valid prefix bytes, at least 0
    private int outputBufferIndex;   // Index of next byte to produce, in the range [0, outputBufferLength]
    
    // Buffer of last 32 KiB of decoded data, for LZ77 decompression
    private byte[] dictionary;
    private int dictionaryIndex;
    
    // Generally speaking, the overall data flow of this decompressor looks like this:
    //   in (the underlying input stream, declared in the superclass) -> in.read()
    //   -> inputBuffer -> packing logic in readBits()
    //   -> inputBitBuffer -> readBit() or equivalent
    //   -> various literal and length-distance symbols -> LZ77 decoding logic
    //   -> dictionary, sometimes also outputBuffer -> copying to the caller's array
    //   -> b (the array passed into this.read(byte[],int,int)).
    
    
    /* Configuration */
    
    // Indicates whether mark() should be called when the underlying
    // input stream is read, and whether calling detach() is allowed.
    private final boolean isDetachable;
    
    
    /* State */
    
    // The state of the decompressor:
    //   -4: The decompressor is in pass-through mode, indefinite uncompressed
    //   -3: This decompressor stream has been closed. Equivalent to in == null.
    //   -2: A data format exception has been thrown. Equivalent to exception != null.
    //   -1: Currently processing a Huffman-compressed block.
    //    0: Initial state, or a block just ended.
    //   1 to 65535: Currently processing an uncompressed block, number of bytes remaining.
    private int state;
    
    // A saved exception that is thrown on every read() or detach().
    private IOException exception;
    
    // Indicates whether a block header with the "bfinal" flag has been seen.
    // This starts as false, should eventually become true, and never changes back to false.
    private boolean isLastBlock;
    
    // Current code trees for when state == -1. When state != -1, both must be null.
    private short[] literalLengthCodeTree;   // When state == -1, this must be not null
    private short[] literalLengthCodeTable;  // Derived from literalLengthCodeTree; same nullness
    private short[] distanceCodeTree;   // When state == -1, this can be null or not null
    private short[] distanceCodeTable;  // Derived from distanceCodeTree; same nullness
    
    
    
    /*---- Constructors ----*/
    
    /**
     * Constructs an inflater input stream over the specified underlying input stream, and with the
     * specified option for detachability. The underlying stream must contain DEFLATE-compressed data with
     * no headers or footers (e.g. must be unwrapped from the zlib or gzip container formats). Detachability
     * allows {@link #detach()} to be called, and requires the specified input stream to support marking.
     * @param in the underlying input stream of raw DEFLATE-compressed data
     * @param detachable whether {@code detach()} can be called later
     * @throws NullPointerException if the input stream is {@code null}
     * @throws IllegalArgumentException if {@code detach == true} but {@code in.markSupported() == false}
     */
    public InflaterInputStream(InputStream in, boolean detachable) {
        this(in, detachable, 16 * 1024);  // Use a reasonable default input buffer size
    }
    
    
    /**
     * Constructs an inflater input stream over the specified underlying input stream, with the
     * specified options for detachability and input buffer size. The underlying stream must
     * contain DEFLATE-compressed data with no headers or footers (e.g. must be unwrapped from
     * the zlib or gzip container formats). Detachability allows {@link #detach()} to be called,
     * and requires the specified input stream to support marking.
     * @param in the underlying input stream of raw DEFLATE-compressed data
     * @param detachable whether {@code detach()} can be called later
     * @param inBufLen the size of the internal read buffer, which must be positive
     * @throws NullPointerException if the input stream is {@code null}
     * @throws IllegalArgumentException if {@code inBufLen < 1}
     * @throws IllegalArgumentException if {@code detach == true} but {@code in.markSupported() == false}
     */
    public InflaterInputStream(InputStream in, boolean detachable, int inBufLen) {
        // Handle the input stream and detachability
        super(in);
        if (inBufLen <= 0)
            throw new IllegalArgumentException("Input buffer size must be positive");
        isDetachable = detachable;
        if (detachable) {
            if (in.markSupported())
                in.mark(0);
            else
                throw new IllegalArgumentException("Input stream not markable, cannot support detachment");
        }
        
        // Initialize data buffers
        inputBuffer = new byte[inBufLen];
        inputBufferLength = 0;
        inputBufferIndex = 0;
        inputBitBuffer = 0;
        inputBitBufferLength = 0;
        outputBuffer = new byte[257];
        outputBufferLength = 0;
        outputBufferIndex = 0;
        dictionary = new byte[DICTIONARY_LENGTH];
        dictionaryIndex = 0;
        
        // Initialize state
        state = 0;
        exception = null;
        isLastBlock = false;
        literalLengthCodeTree = null;
        literalLengthCodeTable = null;
        distanceCodeTree = null;
        distanceCodeTable = null;
    }
    
    /**
     * Extra constructor added for org.eclipse.mat.hprof
     * 
     * Only safe to use copy or original once the underlying stream
     * has been positioned to the appropriate location.
     */
    public InflaterInputStream(InflaterInputStream copy)
    {
        this(copy.in, copy.isDetachable);
        if (copy.inputBuffer != null)
            inputBuffer = Arrays.copyOf(copy.inputBuffer, copy.inputBuffer.length);
        inputBufferLength = copy.inputBufferLength;
        inputBufferIndex = copy.inputBufferIndex;
        inputBitBuffer = copy.inputBitBuffer;
        inputBitBufferLength = copy.inputBitBufferLength;
        if (copy.outputBuffer != null)
            outputBuffer = Arrays.copyOf(copy.outputBuffer, copy.outputBuffer.length);
        outputBufferLength = copy.outputBufferLength;
        outputBufferIndex = copy.outputBufferIndex;
        if (copy.dictionary != null)
            dictionary = Arrays.copyOf(copy.dictionary,  copy.dictionary.length);
        dictionaryIndex = copy.dictionaryIndex;

        // Initialize state
        state = copy.state;
        if (copy.exception != null)
            exception = new IOException(copy.exception.toString());
        isLastBlock = copy.isLastBlock;
        if (copy.literalLengthCodeTree != null)
            literalLengthCodeTree = Arrays.copyOf(copy.literalLengthCodeTree, copy.literalLengthCodeTree.length);
        if (copy.literalLengthCodeTable != null)
            literalLengthCodeTable = Arrays.copyOf(copy.literalLengthCodeTable, copy.literalLengthCodeTable.length);
        if (copy.distanceCodeTree != null)
            distanceCodeTree = Arrays.copyOf(copy.distanceCodeTree, copy.distanceCodeTree.length);
        if (copy.distanceCodeTable != null)
            distanceCodeTable = Arrays.copyOf(copy.distanceCodeTable, copy.distanceCodeTable.length);
    }
    
    
    /*---- Public API methods ----*/
    
    /**
     * Reads the next byte of decompressed data from this stream. If data is available
     * then a number in the range [0, 255] is returned (blocking if necessary);
     * otherwise &minus;1 is returned if the end of stream is reached.
     * @return the next unsigned byte of data, or &minus;1 for the end of stream
     * @throws IOException if an I/O exception occurred in the underlying input stream, the end of
     * stream was encountered at an unexpected position, or the compressed data has a format error
     * @throws IllegalStateException if the stream has already been closed
     */
    public int read() throws IOException {
        // In theory this method for reading a single byte could be implemented somewhat faster.
        // We could take the logic of read(byte[],int,int) and simplify it for the special case
        // of handling one byte. But if the caller chose to use this read() method instead of
        // the bulk read(byte[]) method, then they have already chosen to not care about speed.
        // Therefore speeding up this method would result in needless complexity. Instead,
        // we chose to optimize this method for simplicity and ease of verifying correctness.
        while (true) {
            byte[] b = new byte[1];
            switch (read(b)) {
                case 1:
                    return (b[0] & 0xFF);
                case 0:
                    continue;
                case -1:
                    return -1;
                default:
                    throw new AssertionError();
            }
        }
    }
    
    
    /**
     * Reads some bytes from the decompressed data of this stream into the specified array's subrange.
     * This returns the number of data bytes that were stored into the array, and is in the range
     * [&minus;1, len]. Note that 0 can be returned even if the end of stream hasn't been reached yet.
     * @throws NullPointerException if the array is {@code null}
     * @throws ArrayIndexOutOfBoundsException if the array subrange is out of bounds
     * @throws IOException if an I/O exception occurred in the underlying input stream, the end of
     * stream was encountered at an unexpected position, or the compressed data has a format error
     * @throws IllegalStateException if the stream has already been closed
     */
    public int read(byte[] b, int off, int len) throws IOException {
        // Check arguments and state
        Objects.requireNonNull(b);
        if (off < 0 || off > b.length || len < 0 || b.length - off < len)
            throw new ArrayIndexOutOfBoundsException();
        if (in == null)
            throw new IllegalStateException("Stream already closed");
        if (exception != null)
            throw exception;
        
        // Special handling for empty read request
        if (len == 0)
            return (outputBufferLength > 0 || state != 0 || !isLastBlock) ? 0 : -1;
        assert len > 0;
        
        int result = 0;  // Number of bytes filled in the array 'b'
        
        // First move bytes (if any) from the output buffer
        if (outputBufferLength > 0) {
            int n = Math.min(outputBufferLength - outputBufferIndex, len);
            System.arraycopy(outputBuffer, outputBufferIndex, b, off, n);
            result = n;
            outputBufferIndex += n;
            if (outputBufferIndex == outputBufferLength) {
                outputBufferLength = 0;
                outputBufferIndex = 0;
            }
            if (result == len)
                return result;
        }
        // Now the output buffer is clear, and we have room to read at least one byte
        assert outputBufferLength == 0 && outputBufferIndex == 0 && result < len;
        
        // Get into a block if not already inside one
        while (state == 0) {
            if (isLastBlock)
                return -1;
            
            // Read and process the block header
            isLastBlock = readBits(1) == 1;
            switch (readBits(2)) {  // Type
                case 0:
                    alignInputToByte();
                    state = readBits(16);  // Block length
                    if (state != (readBits(16) ^ 0xFFFF))
                        destroyAndThrow(new DataFormatException("len/nlen mismatch in uncompressed block"));
                    break;
                case 1:
                    state = -1;
                    literalLengthCodeTree  = FIXED_LITERAL_LENGTH_CODE_TREE;
                    literalLengthCodeTable = FIXED_LITERAL_LENGTH_CODE_TABLE;
                    distanceCodeTree  = FIXED_DISTANCE_CODE_TREE;
                    distanceCodeTable = FIXED_DISTANCE_CODE_TABLE;
                    break;
                case 2:
                    state = -1;
                    decodeHuffmanCodes();
                    break;
                case 3:
                    destroyAndThrow(new DataFormatException("Reserved block type"));
                    break;
                default:
                    throw new AssertionError();
            }
        }
        
        // Read the current block's data into the argument array
        if (1 <= state && state <= 0xFFFF) {
            // Read bytes from uncompressed block
            int toRead = Math.min(state, len - result);
            readBytes(b, off + result, toRead);
            for (int i = 0; i < toRead; i++) {
                dictionary[dictionaryIndex] = b[off + result];
                dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                result++;
            }
            state -= toRead;
            return result;
            
        } else if (state == -1)  // Decode symbols from Huffman-coded block
            return result + readInsideHuffmanBlock(b, off + result, len - result);
        else if (state == -4) {
            // Modification for Eclipse MAT
            int n = Math.min(inputBufferLength - inputBufferIndex + (inputBitBufferLength + 7 >> 3), len - result);
            if (n > 0) {
                readBytes(b, off + result, n);
                result += n;
            } else if (result < len) {
                n = len - result;
                int r = in.read(b, off + result, len);
                if (r >= 0) {
                    result += r;
                } else if (result == 0)
                    result = r;
            }
            return result;
        } else
            throw new AssertionError("Impossible state");
    }
    
    
    // This method exists to split up the public read(byte[],int,int) method that is rather long.
    // For internal use only; must only be called by read(byte[],int,int). The input array's subrange must be valid
    // (the caller checks the preconditions). The current state must be -1. This returns a number in the range [0, len].
    private int readInsideHuffmanBlock(byte[] b, int off, int len) throws IOException {
        int result = 0;
        while (result < len) {
            // Try to fill the input bit buffer (somewhat similar to logic in readBits())
            if (inputBitBufferLength < 48) {
                byte[] c = inputBuffer;  // Shorter name
                int i = inputBufferIndex;  // Shorter name
                int numBytes = Math.min((64 - inputBitBufferLength) >>> 3, inputBufferLength - i);
                assert 0 <= numBytes && numBytes <= 8;
                if (numBytes == 2) {  // Only implement special cases that occur frequently in practice
                    inputBitBuffer |= (long)((c[i]&0xFF) | (c[i+1]&0xFF)<<8) << inputBitBufferLength;
                    inputBitBufferLength += 2 * 8;
                    inputBufferIndex += 2;
                } else if (numBytes == 3) {
                    inputBitBuffer |= (long)((c[i]&0xFF) | (c[i+1]&0xFF)<<8 | (c[i+2]&0xFF)<<16) << inputBitBufferLength;
                    inputBitBufferLength += 3 * 8;
                    inputBufferIndex += 3;
                } else if (numBytes == 4) {
                    inputBitBuffer |= (((c[i]&0xFF) | (c[i+1]&0xFF)<<8 | (c[i+2]&0xFF)<<16 | c[i+3]<<24) & 0xFFFFFFFFL) << inputBitBufferLength;
                    inputBitBufferLength += 4 * 8;
                    inputBufferIndex += 4;
                } else {  // This slower general logic is valid for 0 <= numBytes <= 8
                    for (int j = 0; j < numBytes; j++, inputBitBufferLength += 8, inputBufferIndex++)
                        inputBitBuffer |= (c[inputBufferIndex] & 0xFFL) << inputBitBufferLength;
                }
            }
            
            // The worst-case number of bits consumed in one iteration:
            //   length (symbol (15) + extra (5)) + distance (symbol (15) + extra (13)) = 48.
            // This allows us to do decoding entirely from the bit buffer, avoiding the byte buffer or actual I/O.
            if (inputBitBufferLength >= 48) {  // Fast path
                // Decode next literal/length symbol (a customized version of decodeSymbol())
                int sym;
                {
                    int temp = literalLengthCodeTable[(int)inputBitBuffer & CODE_TABLE_MASK];
                    assert temp >= 0;  // No need to mask off sign extension bits
                    int consumed = temp >>> 11;
                    inputBitBuffer >>>= consumed;
                    inputBitBufferLength -= consumed;
                    int node = (temp << 21) >> 21;  // Sign extension from 11 bits
                    while (node >= 0) {
                        node = literalLengthCodeTree[node + ((int)inputBitBuffer & 1)];
                        inputBitBuffer >>>= 1;
                        inputBitBufferLength--;
                    }
                    sym = ~node;
                }
                
                // Handle the symbol by ranges
                assert 0 <= sym && sym <= 287;
                if (sym < 256) {  // Literal byte
                    b[off + result] = (byte)sym;
                    dictionary[dictionaryIndex] = (byte)sym;
                    dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                    result++;
                    
                } else if (sym > 256) {  // Length and distance for copying
                    // Decode the run length (a customized version of decodeRunLength())
                    assert 257 <= sym && sym <= 287;
                    if (sym > 285)
                        destroyAndThrow(new DataFormatException("Reserved run length symbol: " + sym));
                    int run;
                    {
                        int temp = RUN_LENGTH_TABLE[sym - 257];
                        run = temp >>> 3;
                        int numExtraBits = temp & 7;
                        run += (int)inputBitBuffer & ((1 << numExtraBits) - 1);
                        inputBitBuffer >>>= numExtraBits;
                        inputBitBufferLength -= numExtraBits;
                    }
                    assert 3 <= run && run <= 258;
                    
                    // Decode next distance symbol (a customized version of decodeSymbol())
                    if (distanceCodeTree == null)
                        destroyAndThrow(new DataFormatException("Length symbol encountered with empty distance code"));
                    int distSym;
                    {
                        int temp = distanceCodeTable[(int)inputBitBuffer & CODE_TABLE_MASK];
                        assert temp >= 0;  // No need to mask off sign extension bits
                        int consumed = temp >>> 11;
                        inputBitBuffer >>>= consumed;
                        inputBitBufferLength -= consumed;
                        int node = (temp << 21) >> 21;  // Sign extension from 11 bits
                        while (node >= 0) {  // Medium path
                            node = distanceCodeTree[node + ((int)inputBitBuffer & 1)];
                            inputBitBuffer >>>= 1;
                            inputBitBufferLength--;
                        }
                        distSym = ~node;
                    }
                    assert 0 <= distSym && distSym <= 31;
                    
                    // Decode the distance (a customized version of decodeDistance())
                    if (distSym > 29)
                        destroyAndThrow(new DataFormatException("Reserved distance symbol: " + distSym));
                    int dist;
                    {
                        int temp = DISTANCE_TABLE[distSym];
                        dist = temp >>> 4;
                        int numExtraBits = temp & 0xF;
                        dist += (int)inputBitBuffer & ((1 << numExtraBits) - 1);
                        inputBitBuffer >>>= numExtraBits;
                        inputBitBufferLength -= numExtraBits;
                    }
                    assert 1 <= dist && dist <= 32768;
                    assert inputBitBufferLength >= 0;
                    
                    // Copy bytes to output and dictionary
                    int dictReadIndex = (dictionaryIndex - dist) & DICTIONARY_MASK;
                    if (len - result >= run) {  // Nice case with less branching
                        for (int i = 0; i < run; i++) {
                            byte bb = dictionary[dictReadIndex];
                            dictionary[dictionaryIndex] = bb;
                            b[off + result] = bb;
                            dictReadIndex = (dictReadIndex + 1) & DICTIONARY_MASK;
                            dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                            result++;
                        }
                    } else {  // General case
                        for (int i = 0; i < run; i++) {
                            byte bb = dictionary[dictReadIndex];
                            dictionary[dictionaryIndex] = bb;
                            dictReadIndex = (dictReadIndex + 1) & DICTIONARY_MASK;
                            dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                            if (result < len) {
                                b[off + result] = bb;
                                result++;
                            } else {
                                assert outputBufferLength < outputBuffer.length;
                                outputBuffer[outputBufferLength] = bb;
                                outputBufferLength++;
                            }
                        }
                    }
                    
                } else {  // sym == 256, end of block
                    literalLengthCodeTree = null;
                    literalLengthCodeTable = null;
                    distanceCodeTree = null;
                    distanceCodeTable = null;
                    state = 0;
                    break;
                }
                
            } else {  // General case (always correct), when not enough bits in buffer to guarantee reading
                int sym = decodeSymbol(literalLengthCodeTree);
                assert 0 <= sym && sym <= 287;
                if (sym < 256) {  // Literal byte
                    b[off + result] = (byte)sym;
                    dictionary[dictionaryIndex] = (byte)sym;
                    dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                    result++;
                } else if (sym > 256) {  // Length and distance for copying
                    int run = decodeRunLength(sym);
                    assert 3 <= run && run <= 258;
                    if (distanceCodeTree == null)
                        destroyAndThrow(new DataFormatException("Length symbol encountered with empty distance code"));
                    int distSym = decodeSymbol(distanceCodeTree);
                    assert 0 <= distSym && distSym <= 31;
                    int dist = decodeDistance(distSym);
                    assert 1 <= dist && dist <= 32768;
                    
                    // Copy bytes to output and dictionary
                    int dictReadIndex = (dictionaryIndex - dist) & DICTIONARY_MASK;
                    for (int i = 0; i < run; i++) {
                        byte bb = dictionary[dictReadIndex];
                        dictReadIndex = (dictReadIndex + 1) & DICTIONARY_MASK;
                        dictionary[dictionaryIndex] = bb;
                        dictionaryIndex = (dictionaryIndex + 1) & DICTIONARY_MASK;
                        if (result < len) {
                            b[off + result] = bb;
                            result++;
                        } else {
                            assert outputBufferLength < outputBuffer.length;
                            outputBuffer[outputBufferLength] = bb;
                            outputBufferLength++;
                        }
                    }
                } else {  // sym == 256, end of block
                    literalLengthCodeTree = null;
                    literalLengthCodeTable = null;
                    distanceCodeTree = null;
                    distanceCodeTable = null;
                    state = 0;
                    break;
                }
            }
        }
        return result;
    }
    
    
    /**
     * Detaches the underlying input stream from this decompressor. This puts the underlying stream
     * at the position of the first byte after the data that this decompressor actually consumed.
     * Calling {@code detach()} invalidates this stream object but doesn't close the underlying stream.
     * <p>This method exists because for efficiency, the decompressor may read more bytes from the
     * underlying stream than necessary to produce the decompressed data. If you want to continue
     * reading the underlying stream exactly after the point the DEFLATE-compressed data ends,
     * then it is necessary to call this detach method.</p>
     * <p>This can only be called once, and is mutually exclusive with respect to calling
     * {@link #close()}. It is illegal to call {@link #read()} after detaching.</p>
     * @throws IllegalStateException if detach was already called or this stream has been closed
     * @throws IOException if an I/O exception occurred
     */
    public void detach() throws IOException {
        // Modifications for Eclipse MAT
        //if (!isDetachable)
        //    throw new IllegalStateException("Detachability not specified at construction");
        if (in == null)
            throw new IllegalStateException("Input stream already detached/closed");
        if (exception != null)
            throw exception;
        if (!isDetachable)
        {
            if (state == -4)
                throw new IllegalStateException("Input stream already detached");
            state = -4;
            return;
        }
        
        // Rewind the underlying stream, then skip over bytes that were already consumed.
        // Note that a byte with some bits consumed is considered to be fully consumed.
        in.reset();
        int skip = inputBufferIndex - inputBitBufferLength / 8;
        assert skip >= 0;
        while (skip > 0) {
            long n = in.skip(skip);
            if (n <= 0)
                throw new EOFException();
            skip -= n;
        }
        
        in = null;
        state = -3;
        destroyState();
    }

    /**
     * Resume decompression.
     * Addition for Eclipse MAT
     */
    public void attach()
    {
        if (state != -4)
            throw new IllegalStateException("Not detached");
        Arrays.fill(dictionary, (byte)0);
        dictionaryIndex = 0;
        isLastBlock = false;
        state = 0;
    }

    /**
     * Closes this input stream and the underlying stream.
     * It is illegal to call {@link #read()} or {@link #detach()} after closing.
     * It is idempotent to call this {@link #close()} method more than once.
     * @throws IOException if an I/O exception occurred in the underlying stream
     */
    public void close() throws IOException {
        if (in == null)
            return;
        super.close();
        in = null;
        state = -3;
        exception = null;
        destroyState();
    }
    
    
    /*---- Huffman coding methods ----*/
    
    // Reads the current block's dynamic Huffman code tables from from the input buffers/stream,
    // processes the code lengths and computes the code trees, and ultimately sets just the variables
    // {literalLengthCodeTree, literalLengthCodeTable, distanceCodeTree, distanceCodeTable}.
    // This might throw an IOException for actual I/O exceptions, unexpected end of stream,
    // or a description of an invalid Huffman code.
    private void decodeHuffmanCodes() throws IOException {
        int numLitLenCodes  = readBits(5) + 257;  // hlit  + 257
        int numDistCodes    = readBits(5) +   1;  // hdist +   1
        
        // Read the code length code lengths
        int numCodeLenCodes = readBits(4) +   4;  // hclen +   4
        byte[] codeLenCodeLen = new byte[19];  // This array is filled in a strange order
        for (int i = 0; i < numCodeLenCodes; i++)
            codeLenCodeLen[CODE_LENGTH_CODE_ORDER[i]] = (byte)readBits(3);
        short[] codeLenCodeTree;
        try {
            codeLenCodeTree = codeLengthsToCodeTree(codeLenCodeLen);
        } catch (DataFormatException e) {
            destroyAndThrow(e);
            throw new AssertionError("Unreachable");
        }
        
        // Read the main code lengths and handle runs
        byte[] codeLens = new byte[numLitLenCodes + numDistCodes];
        byte runVal = -1;
        int runLen = 0;
        for (int i = 0; i < codeLens.length; ) {
            if (runLen > 0) {
                assert runVal != -1;
                codeLens[i] = runVal;
                runLen--;
                i++;
            } else {
                int sym = decodeSymbol(codeLenCodeTree);
                assert 0 <= sym && sym <= 18;
                if (sym < 16) {
                    runVal = codeLens[i] = (byte)sym;
                    i++;
                } else if (sym == 16) {
                    if (runVal == -1)
                        destroyAndThrow(new DataFormatException("No code length value to copy"));
                    runLen = readBits(2) + 3;
                } else if (sym == 17) {
                    runVal = 0;
                    runLen = readBits(3) + 3;
                } else {  // sym == 18
                    runVal = 0;
                    runLen = readBits(7) + 11;
                }
            }
        }
        if (runLen > 0)
            destroyAndThrow(new DataFormatException("Run exceeds number of codes"));
        
        // Create literal-length code tree
        byte[] litLenCodeLen = Arrays.copyOf(codeLens, numLitLenCodes);
        try {
            literalLengthCodeTree = codeLengthsToCodeTree(litLenCodeLen);
        } catch (DataFormatException e) {
            destroyAndThrow(e);
            throw new AssertionError("Unreachable");
        }
        literalLengthCodeTable = codeTreeToCodeTable(literalLengthCodeTree);
        
        // Create distance code tree with some extra processing
        byte[] distCodeLen = Arrays.copyOfRange(codeLens, numLitLenCodes, codeLens.length);
        if (distCodeLen.length == 1 && distCodeLen[0] == 0)
            distanceCodeTree = null;  // Empty distance code; the block shall be all literal symbols
        else {
            // Get statistics for upcoming logic
            int oneCount = 0;
            int otherPositiveCount = 0;
            for (byte x : distCodeLen) {
                if (x == 1)
                    oneCount++;
                else if (x > 1)
                    otherPositiveCount++;
            }
            
            // Handle the case where only one distance code is defined
            if (oneCount == 1 && otherPositiveCount == 0) {
                // Add a dummy invalid code to make the Huffman tree complete
                distCodeLen = Arrays.copyOf(distCodeLen, 32);
                distCodeLen[31] = 1;
            }
            try {
                distanceCodeTree = codeLengthsToCodeTree(distCodeLen);
            } catch (DataFormatException e) {
                destroyAndThrow(e);
                throw new AssertionError("Unreachable");
            }
            distanceCodeTable = codeTreeToCodeTable(distanceCodeTree);
        }
    }
    
    
    /* 
     * Converts the given array of symbol code lengths into a canonical code tree.
     * A symbol code length is either zero (absent from the tree) or a positive integer.
     * 
     * A code tree is an array of integers, where each pair represents a node.
     * Each pair is adjacent and starts on an even index. The first element of
     * the pair represents the left child and the second element represents the
     * right child. The root node is at index 0. If an element is non-negative,
     * then it is the index of the child node in the array. Otherwise it is the
     * bitwise complement of the leaf symbol. This tree is used in decodeSymbol()
     * and codeTreeToCodeTable(). Not every element of the array needs to be
     * used, nor do used elements need to be contiguous.
     * 
     * For example, this Huffman tree:
     *        o
     *       / \
     *      o   \
     *     / \   \
     *   'a' 'b' 'c'
     * is serialized as this array:
     *   {2, ~'c', ~'a', ~'b'}
     * because the root is located at index 0 and the other internal node is
     * located at index 2.
     */
    private static short[] codeLengthsToCodeTree(byte[] codeLengths) throws DataFormatException {
        // Allocate array for the worst case if all symbols are present
        short[] result = new short[(codeLengths.length - 1) * 2];
        Arrays.fill(result, CODE_TREE_UNUSED_SLOT);
        result[0] = CODE_TREE_OPEN_SLOT;
        result[1] = CODE_TREE_OPEN_SLOT;
        int allocated = 2;  // Always even in this algorithm
        
        int maxCodeLen = 0;
        for (byte cl : codeLengths) {
            assert 0 <= cl && cl <= 15;
            maxCodeLen = Math.max(cl, maxCodeLen);
        }
        if (maxCodeLen > 15)
            throw new AssertionError("Maximum code length exceeds DEFLATE specification");
        
        // Allocate Huffman tree nodes according to ascending code lengths
        for (int curCodeLen = 1; curCodeLen <= maxCodeLen; curCodeLen++) {
            // Loop invariant: Each OPEN child slot in the result array has depth curCodeLen
            
            // Allocate all symbols of current code length to open slots in ascending order
            int resultIndex = 0;
            for (int symbol = 0; ; ) {
                // Find next symbol having current code length
                while (symbol < codeLengths.length && codeLengths[symbol] != curCodeLen)
                    symbol++;
                if (symbol == codeLengths.length)
                    break;  // No more symbols to process
                
                // Find next open child slot
                while (resultIndex < allocated && result[resultIndex] != CODE_TREE_OPEN_SLOT)
                    resultIndex++;
                if (resultIndex == allocated)  // No more slots left
                    throw new DataFormatException("Canonical code fails to produce full Huffman code tree");
                
                // Put the symbol into the slot and increment
                result[resultIndex] = (short)~symbol;
                resultIndex++;
                symbol++;
            }
            
            // Take all open slots and deepen them by one level
            for (int end = allocated; resultIndex < end; resultIndex++) {
                if (result[resultIndex] == CODE_TREE_OPEN_SLOT) {
                    // Allocate a new node
                    assert allocated + 2 <= result.length;
                    result[resultIndex] = (short)allocated;
                    result[allocated + 0] = CODE_TREE_OPEN_SLOT;
                    result[allocated + 1] = CODE_TREE_OPEN_SLOT;
                    allocated += 2;
                }
            }
        }
        
        // Check for unused open slots after all symbols are allocated
        for (int i = 0; i < allocated; i++) {
            if (result[i] == CODE_TREE_OPEN_SLOT)
                throw new DataFormatException("Canonical code fails to produce full Huffman code tree");
        }
        return result;
    }
    
    
    /* 
     * Converts a code tree array into a fast look-up table that consumes up to
     * CODE_TABLE_BITS at once. Each entry i in the table encodes the result of
     * decoding starting from the root and consuming the bits of i starting from
     * the lowest-order bits.
     * 
     * Each array element encodes (numBitsConsumed << 11) | (node & 0x7FF), where:
     * - numBitsConsumed is a 4-bit unsigned integer in the range [1, CODE_TABLE_BITS].
     * - node is an 11-bit signed integer representing either the current node
     *   (which is a non-negative number) after consuming all the available bits
     *   from i, or the bitwise complement of the decoded symbol (so it's negative).
     * Note that each element is a non-negative number.
     */
    private static short[] codeTreeToCodeTable(short[] codeTree) {
        assert 1 <= CODE_TABLE_BITS && CODE_TABLE_BITS <= 15;
        short[] result = new short[1 << CODE_TABLE_BITS];
        for (int i = 0; i < result.length; i++) {
            // Simulate decodeSymbol() using the bits of i
            int node = 0;
            int consumed = 0;
            do {
                node = codeTree[node + ((i >>> consumed) & 1)];
                consumed++;
            } while (node >= 0 && consumed < CODE_TABLE_BITS);
            
            assert 1 <= consumed && consumed <= 15;  // 4 bits unsigned
            assert -1024 <= node && node <= 1023;  // 11 bits signed
            result[i] = (short)(consumed << 11 | (node & 0x7FF));
            assert result[i] >= 0;
        }
        return result;
    }
    
    
    // Reads bits from the input buffers/stream and uses the given code tree to decode the next symbol.
    // The returned symbol value is a non-negative integer. This throws an IOException if the end of stream
    // is reached before a symbol is decoded, or if the underlying stream experiences an I/O exception.
    private int decodeSymbol(short[] codeTree) throws IOException {
        int node = 0;  // An index into the codeTree array which signifies the current tree node
        while (node >= 0) {
            if (inputBitBufferLength > 0) {  // Medium path using buffered bits
                node = codeTree[node + ((int)inputBitBuffer & 1)];
                inputBitBuffer >>>= 1;
                inputBitBufferLength--;
            } else  // Slow path with potential I/O operations
                node = codeTree[node + readBits(1)];
        }
        return ~node;  // Symbol was encoded as bitwise complement
    }
    
    
    // Takes the given run length symbol in the range [257, 287], possibly reads some more input bits,
    // and returns a number in the range [3, 258]. This throws an IOException if bits needed to be read
    // but the end of stream was reached or the underlying stream experienced an I/O exception.
    private int decodeRunLength(int sym) throws IOException {
        assert 257 <= sym && sym <= 287;
        if (sym <= 264)
            return sym - 254;
        else if (sym <= 284) {
            int numExtraBits = (sym - 261) >>> 2;
            return ((((sym - 1) & 3) | 4) << numExtraBits) + 3 + readBits(numExtraBits);
        } else if (sym == 285)
            return 258;
        else {  // sym is 286 or 287
            destroyAndThrow(new DataFormatException("Reserved run length symbol: " + sym));
            throw new AssertionError("Unreachable");
        }
    }
    
    
    // Takes the given run length symbol in the range [0, 31], possibly reads some more input bits,
    // and returns a number in the range [1, 32768]. This throws an IOException if bits needed to
    // be read but the end of stream was reached or the underlying stream experienced an I/O exception.
    private int decodeDistance(int sym) throws IOException {
        assert 0 <= sym && sym <= 31;
        if (sym <= 3)
            return sym + 1;
        else if (sym <= 29) {
            int numExtraBits = (sym >>> 1) - 1;
            return (((sym & 1) | 2) << numExtraBits) + 1 + readBits(numExtraBits);
        } else {  // sym is 30 or 31
            destroyAndThrow(new DataFormatException("Reserved distance symbol: " + sym));
            throw new AssertionError("Unreachable");
        }
    }
    
    
    /*---- I/O methods ----*/
    
    // Returns the given number of least significant bits from the bit buffer.
    // This updates the bit buffer state and possibly also the byte buffer state.
    private int readBits(int numBits) throws IOException {
        // Check arguments and invariants
        assert 1 <= numBits && numBits <= 16;  // Note: DEFLATE uses up to 16, but this method is correct up to 31
        assert 0 <= inputBitBufferLength && inputBitBufferLength <= 63;
        assert inputBitBuffer >>> inputBitBufferLength == 0;  // Ensure high-order bits are clean
        
        // Ensure there is enough data in the bit buffer to satisfy the request
        while (inputBitBufferLength < numBits) {
            while (inputBufferIndex >= inputBufferLength)  // Fill and retry
                fillInputBuffer();
            
            // Pack as many bytes as possible from input byte buffer into the bit buffer
            int numBytes = Math.min((64 - inputBitBufferLength) >>> 3, inputBufferLength - inputBufferIndex);
            if (numBytes <= 0)
                throw new AssertionError("Impossible state");
            for (int i = 0; i < numBytes; i++, inputBitBufferLength += 8, inputBufferIndex++)
                inputBitBuffer |= (inputBuffer[inputBufferIndex] & 0xFFL) << inputBitBufferLength;
            assert inputBitBufferLength <= 64;  // Can temporarily be 64
        }
        
        // Extract the bits to return
        int result = (int)inputBitBuffer & ((1 << numBits) - 1);
        inputBitBuffer >>>= numBits;
        inputBitBufferLength -= numBits;
        
        // Check return and recheck invariants
        assert result >>> numBits == 0;
        assert 0 <= inputBitBufferLength && inputBitBufferLength <= 63;
        assert inputBitBuffer >>> inputBitBufferLength == 0;
        return result;
    }
    
    
    // Reads exactly 'len' bytes from {the input buffers or underlying input stream} into the
    // given array subrange. This method alters the input buffer states, may throw an IOException,
    // and would destroy the decompressor state if EOF occurs before the read length is satisfied.
    private void readBytes(byte[] b, int off, int len) throws IOException {
        // Check bit buffer invariants
        if (inputBitBufferLength < 0 || inputBitBufferLength > 63
                || inputBitBuffer >>> inputBitBufferLength != 0)
            throw new AssertionError("Invalid input bit buffer state");
        
        // First unpack saved bits
        alignInputToByte();
        for (; len > 0 && inputBitBufferLength >= 8; off++, len--) {
            b[off] = (byte)inputBitBuffer;
            inputBitBuffer >>>= 8;
            inputBitBufferLength -= 8;
        }
        
        // Read from input buffer
        {
            int n = Math.min(len, inputBufferLength - inputBufferIndex);
            assert inputBitBufferLength == 0 || n == 0;
            System.arraycopy(inputBuffer, inputBufferIndex, b, off, n);
            inputBufferIndex += n;
            off += n;
            len -= n;
        }
        
        // Read directly from input stream (without putting into input buffer)
        while (len > 0) {
            assert inputBufferIndex == inputBufferLength;
            int n = in.read(b, off, len);
            if (n == -1)
                destroyAndThrow(new EOFException("Unexpected end of stream"));
            off += n;
            len -= n;
        }
    }
    
    
    // Fills the input byte buffer with new data read from the underlying input stream.
    // Requires the buffer to be fully consumed before being called. This method sets
    // inputBufferLength to a value in the range [-1, inputBuffer.length] and inputBufferIndex to 0.
    private void fillInputBuffer() throws IOException {
        if (state < -1)
            throw new AssertionError("Must not read in this state");
        if (inputBufferIndex < inputBufferLength)
            throw new AssertionError("Input buffer not fully consumed yet");
        
        if (isDetachable)
            in.mark(inputBuffer.length);
        inputBufferLength = in.read(inputBuffer);
        inputBufferIndex = 0;
        if (inputBufferLength == -1)
            destroyAndThrow(new EOFException("Unexpected end of stream"));  // Note: This sets inputBufferLength to 0
        if (inputBufferLength < -1 || inputBufferLength > inputBuffer.length)
            throw new AssertionError();
    }
    
    
    // Discards the remaining bits (0 to 7) in the current byte being read, if any. Always succeeds.
    private void alignInputToByte() {
        int discard = inputBitBufferLength & 7;
        inputBitBuffer >>>= discard;
        inputBitBufferLength -= discard;
        assert inputBitBufferLength % 8 == 0;
    }
    
    
    /*---- State management methods ----*/
    
    // Throws an IOException with the given reason, and destroys the state of this decompressor.
    private void destroyAndThrow(IOException e) throws IOException {
        state = -2;
        exception = e;
        destroyState();
        // Do not set 'in' to null, so that calling close() is still possible
        throw e;
    }
    
    
    // Clears all state variables except {in, state, exception}, to prevent accidental use of the
    // stream thereafter. It is illegal to call read() or detach() after this method is called.
    // The caller is responsible for manipulating the other state variables appropriately.
    private void destroyState() {
        isLastBlock = true;
        literalLengthCodeTree = null;
        literalLengthCodeTable = null;
        distanceCodeTree = null;
        distanceCodeTable = null;
        
        inputBuffer = null;
        inputBufferLength = 0;
        inputBufferIndex = 0;
        inputBitBuffer = 0;
        inputBitBufferLength = 0;
        outputBuffer = null;
        outputBufferLength = 0;
        outputBufferIndex = 0;
        dictionary = null;
        dictionaryIndex = 0;
    }
    
    
    /*---- Constants and tables ----*/
    
    private static final int[] CODE_LENGTH_CODE_ORDER =
        {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
    
    private static final short[] FIXED_LITERAL_LENGTH_CODE_TREE;
    private static final short[] FIXED_LITERAL_LENGTH_CODE_TABLE;
    private static final short[] FIXED_DISTANCE_CODE_TREE;
    private static final short[] FIXED_DISTANCE_CODE_TABLE;
    
    // For use in codeLengthsToCodeTree() only.
    private static final short CODE_TREE_UNUSED_SLOT = 0x7000;
    private static final short CODE_TREE_OPEN_SLOT   = 0x7002;
    
    // Any integer from 1 to 15 is valid. Affects speed but produces same output.
    private static final int CODE_TABLE_BITS = 9;
    private static final int CODE_TABLE_MASK = (1 << CODE_TABLE_BITS) - 1;
    
    
    static {
        byte[] llcodelens = new byte[288];
        Arrays.fill(llcodelens,   0, 144, (byte)8);
        Arrays.fill(llcodelens, 144, 256, (byte)9);
        Arrays.fill(llcodelens, 256, 280, (byte)7);
        Arrays.fill(llcodelens, 280, 288, (byte)8);
        
        byte[] distcodelens = new byte[32];
        Arrays.fill(distcodelens, (byte)5);
        
        try {
            FIXED_LITERAL_LENGTH_CODE_TREE = codeLengthsToCodeTree(llcodelens);
            FIXED_DISTANCE_CODE_TREE = codeLengthsToCodeTree(distcodelens);
        } catch (DataFormatException e) {
            throw new AssertionError(e);
        }
        FIXED_LITERAL_LENGTH_CODE_TABLE = codeTreeToCodeTable(FIXED_LITERAL_LENGTH_CODE_TREE);
        FIXED_DISTANCE_CODE_TABLE = codeTreeToCodeTable(FIXED_DISTANCE_CODE_TREE);
    }
    
    
    // Must be a power of 2. Do not change this constant value. If the value is decreased, then
    // decompression may produce different data that violates the DEFLATE spec (but no crashes).
    // If the value is increased, the behavior stays the same but memory is wasted with no benefit.
    private static final int DICTIONARY_LENGTH = 32 * 1024;
    
    // This is why the above must be a power of 2.
    private static final int DICTIONARY_MASK = DICTIONARY_LENGTH - 1;
    
    
    // For length symbols from 257 to 285 (inclusive). RUN_LENGTH_TABLE[i] =
    // (base of run length) << 3 | (number of extra bits to read).
    private static final short[] RUN_LENGTH_TABLE = {24, 32, 40, 48, 56, 64, 72, 80, 89, 105, 121, 137,
        154, 186, 218, 250, 283, 347, 411, 475, 540, 668, 796, 924, 1053, 1309, 1565, 1821, 2064};
    
    // For length symbols from 0 to 29 (inclusive). DISTANCE_TABLE[i] =
    // (base of distance) << 4 | (number of extra bits to read).
    private static final int[] DISTANCE_TABLE = {16, 32, 48, 64, 81, 113, 146, 210, 275, 403, 532, 788, 1045, 1557,
        2070, 3094, 4119, 6167, 8216, 12312, 16409, 24601, 32794, 49178, 65563, 98331, 131100, 196636, 262173, 393245};
    
}
