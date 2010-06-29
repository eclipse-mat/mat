#!/bin/sh
#
# This script parses a heap dump.
#
# Usage: ParseHeapDump.sh <path/to/dump.hprof> [report]*
#
# The leak report has the id org.eclipse.mat.api:suspects
#

./MemoryAnalyzer -consolelog -application org.eclipse.mat.api.parse "$@"
