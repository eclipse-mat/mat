#!/bin/sh
#
# This script parses a heap dump.
#
# Usage: ParseHeapDump.sh <path/to/dump.hprof> [report]*
#
# The leak report has the id org.eclipse.mat.api:suspects
# The top component report has the id org.eclipse.mat.api:top_components
#

"$(dirname -- "$0")"/MemoryAnalyzer -consolelog -nosplash -application org.eclipse.mat.api.parse "$@"
