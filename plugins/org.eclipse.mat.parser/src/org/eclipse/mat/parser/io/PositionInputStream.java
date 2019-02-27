package org.eclipse.mat.parser.io;

import java.io.DataInput;
import java.io.IOException;

public interface PositionInputStream extends DataInput
{

    int read() throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    long skip(long n) throws IOException;

    boolean markSupported();

    void mark(int readLimit);

    void reset();

    int skipBytes(int n) throws IOException;

    int skipBytes(long n) throws IOException;

    void readFully(byte b[]) throws IOException;

    void readFully(byte b[], int off, int len) throws IOException;

    long position();

    void seek(long pos) throws IOException;

    int readUnsignedByte() throws IOException;

    int readInt() throws IOException;

    long readLong() throws IOException;

    boolean readBoolean() throws IOException;

    byte readByte() throws IOException;

    char readChar() throws IOException;

    double readDouble() throws IOException;

    float readFloat() throws IOException;

    String readLine() throws IOException;

    short readShort() throws IOException;

    String readUTF() throws IOException;

    int readUnsignedShort() throws IOException;

    long readUnsignedInt() throws IOException;

    long readID(int idSize) throws IOException;
    
    void close() throws IOException;

}
