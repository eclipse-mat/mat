package org.eclipse.mat.inspections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IObject;

public class SunClassSpecificNameResolvers
{
    @Subject("sun.nio.fs.UnixPath")
    public static class UnixPathNameResolver implements IClassSpecificNameResolver
    {
        public static String resolve_(IObject object) throws SnapshotException {
            IObject stringValue = (IObject) object.resolveValue("stringValue");
            if (stringValue != null)
                return stringValue.getClassSpecificName();

            IObject path = (IObject) object.resolveValue("path"); // byte[]

            return path.getClassSpecificName();
        }

        public String resolve(IObject object) throws SnapshotException
        {
            return resolve_(object);
        }
    }
}
