/*******************************************************************************
 * Copyright (c) 2008, 2016 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial implementation - versions as int constants
 *    James Livingston - converted the versions to an enum
 *******************************************************************************/
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
    IBM19,
    JAVA18,
    JAVA19;

    // helpers
    public static EnumSet<JdkVersion> ALL = EnumSet.allOf(JdkVersion.class);
    public static EnumSet<JdkVersion> of(JdkVersion first, JdkVersion... rest) {
        return EnumSet.of(first, rest);
    }
    public static EnumSet<JdkVersion> except(JdkVersion first, JdkVersion... rest) {
        return EnumSet.complementOf(EnumSet.of(first, rest));
    }
}