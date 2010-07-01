#!/bin/sh
#
# This script parses a heap dump.
# Suitable for 64-bit and 32-bit Java
#
# Usage: ParseHeapDump2.sh <path/to/dump.dmp.zip> [report]*
#
# The leak report has the id org.eclipse.mat.api:suspects
# The top component report has the id org.eclipse.mat.api:top_components
#

java -Xmx1024m -jar "`dirname "$0"`"/plugins/org.eclipse.equinox.launcher_1*.jar -consoleLog -application org.eclipse.mat.api.parse "$@"
