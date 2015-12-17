package org.eclipse.mat.snapshot.extension;

import java.util.EnumSet;

/**
 * Enumeration of known JDK collection versions
 * 
 * @since 1.6
 */
public enum JdkVersion {
    SUN,
    IBM14,
    IBM15,
    IBM16, // Harmony based collections
    IBM17, // Oracle based collections
    IBM18,
    JAVA18;

    // helpers
    public static EnumSet<JdkVersion> ALL = EnumSet.allOf(JdkVersion.class);
    public static EnumSet<JdkVersion> of(JdkVersion first, JdkVersion... rest) {
        return EnumSet.of(first, rest);
    }
    public static EnumSet<JdkVersion> except(JdkVersion first, JdkVersion... rest) {
        return EnumSet.complementOf(EnumSet.of(first, rest));
    }
}