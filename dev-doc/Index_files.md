# Index files

Memory Analyzer uses several indexes to enable access to different parts of the
snapshot.

| Index name | description |
|------------|-------------|
| IDENTIFIER | IntToLong object ID to object address |
| O2CLASS | object ID to class ID |
| A2SIZE | array object ID (or other non-fixed size object) to encoded size (32-bits) |
| INBOUND | object ID to list of object IDs |
| OUTBOUND | object ID to list of object IDs |
| DOMINATED | Dominated: object id to N dominated object ids |
| O2RETAINED | object ID to long |
| DOMINATOR | Dominator of: object id to the id of its dominator |
| I2RETAINED | cache of size of class, classloader (read/write) |

## IntIndexReader

For an index file like O2CLASS, the file is stored as many `ArrayIntCompressed`
followed by an index: `IntIndexReader`. There is a special adjustment to cope
with >2^31 entries files as that can be needed for 1 to N files.

On reading there is a `SoftReference` cache of those `ArrayIntCompressed` pages.

The data is read using a `SimpleBufferedRandomAccessInputStream` which just has
local buffer.

## LongIndexReader

LongIndexReader is similar (without the adjustment for 2^31 entries).

## PositionIndexReader

PositionIndexReader is similar (without the adjustment for 2^31 entries).

## 1 to N reader

`IntIndex1NReader` has two parts: a body and an header, and a final `long` of
the position of the split in the file.

For an input, use the header to find the position of the start in the body and
the header via (index+1) to find the position of the next entry.
Read data between the two from the body.

## Random Access File caching

HPROF random access to GZIP compressed files to read fields and array contents

- `org.eclipse.mat.hprof.DefaultPositionInputStream`
    - `org.eclipse.mat.parser.io.BufferedRandomAccessInputStream`
        - `HashMapLongObject`
            - `[*N] page`
                - `[*N] SoftReference`
                    - `buffer byte[512]`
        - `org.eclipse.mat.hprof.CompressedRandomAccessFile`
            - `org.eclipse.mat.hprof.SeekableStream`
                - `[*N} org.eclipse.mat.hprof.SeekableStream$PosStream`
                    - `SoftReference`
                        - `org.eclipse.mat.hprof.GZIPInputStream2`
                            - `io.nayuki.deflate.InflaterInputStream`
                                - `inputBuffer byte[16384]`
                                    - `dictionary byte[32768]`
