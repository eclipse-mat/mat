#!/bin/sh
#
# This script parses a heap dump.
# Adjust the path to java, version 5 or later, and the heap size as required.
# Suitable for 64-bit and 32-bit Java, but a 64-bit Java is required
# for larger heap sizes.
#
# Usage: ParseHeapDump.sh <path/to/dump.dmp.zip> [report]*
#
# The leak report has the id org.eclipse.mat.api:suspects
# The top component report has the id org.eclipse.mat.api:top_components
#

java -Xmx3072M -jar "$(dirname -- "$0")"/plugins/org.eclipse.equinox.launcher_1*.jar -consoleLog -nosplash -application org.eclipse.mat.api.parse "$@"
