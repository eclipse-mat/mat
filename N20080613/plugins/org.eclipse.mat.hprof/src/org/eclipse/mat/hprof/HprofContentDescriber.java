package org.eclipse.mat.hprof;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.content.IContentDescriber;
import org.eclipse.core.runtime.content.IContentDescription;

public class HprofContentDescriber implements IContentDescriber
{
    private static final QualifiedName[] QUALIFIED_NAMES = new QualifiedName[] { new QualifiedName("java-heap-dump",
                    "hprof") };

    public int describe(InputStream contents, IContentDescription description) throws IOException
    {
        return AbstractParser.readVersion(contents) != null ? VALID : INVALID;
    }

    public QualifiedName[] getSupportedOptions()
    {
        return QUALIFIED_NAMES;
    }

}
