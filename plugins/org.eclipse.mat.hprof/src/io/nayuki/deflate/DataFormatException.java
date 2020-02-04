/* 
 * DEFLATE library (Java)
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/deflate-library-java
 * https://github.com/nayuki/DEFLATE-library-Java
 */

package io.nayuki.deflate;

import java.io.IOException;


public final class DataFormatException extends IOException {
    
    public DataFormatException(String msg) {
        super(msg);
    }
    
    
    public DataFormatException(Throwable cause) {
        super(cause);
    }
    
}
