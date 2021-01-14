/*******************************************************************************
 * Copyright (c) 2008, 2019 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Netflix (Jason Koch) - refactors for increased performance and concurrency
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.DataInput;
import java.io.IOException;

public interface IPositionInputStream extends DataInput
{

    int read() throws IOException;

    int read(byte[] b, int off, int len) throws IOException;

    long skip(long n) throws IOException;

    boolean markSupported();

    void mark(int readLimit);

    void reset() throws IOException;

    @Override
    int skipBytes(int n) throws IOException;

    int skipBytes(long n) throws IOException;

    @Override
    void readFully(byte b[]) throws IOException;

    @Override
    void readFully(byte b[], int off, int len) throws IOException;

    long position();

    void seek(long pos) throws IOException;

    @Override
    int readUnsignedByte() throws IOException;

    @Override
    int readInt() throws IOException;

    @Override
    long readLong() throws IOException;

    @Override
    boolean readBoolean() throws IOException;

    @Override
    byte readByte() throws IOException;

    @Override
    char readChar() throws IOException;

    @Override
    double readDouble() throws IOException;

    @Override
    float readFloat() throws IOException;

    @Override
    String readLine() throws IOException;

    @Override
    short readShort() throws IOException;

    @Override
    String readUTF() throws IOException;

    @Override
    int readUnsignedShort() throws IOException;

    long readUnsignedInt() throws IOException;

    long readID(int idSize) throws IOException;
    
    void close() throws IOException;

}
