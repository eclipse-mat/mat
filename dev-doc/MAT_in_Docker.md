# MAT in Docker

It is possible to run Eclipse Memory Analyzer in a Docker container. A useful Docker image is the [following](https://hub.docker.com/r/kgibm/fedorawasdebug)

It is also possible to have minimal images to allow Eclipse Memory Analyzer to be tested in various Linux distributions. These Dockerfiles allow testing of snapshot builds.

```
FROM ubuntu
#If docker build gets the wrong time , might need apt-get -o Acquire::Max-FutureTime=86400 update
RUN apt-get update && apt-get install -y default-jdk wget unzip libwebkit2gtk-4.0 firefox
# Download snapshot build, just for testing
RUN wget "http://www.eclipse.org/downloads/download.php?file=/mat/snapshots/rcp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip&mirror_id=1" -O /tmp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip
RUN unzip /tmp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip -d /opt
ENV DISPLAY host.docker.internal:0.0
CMD ["/opt/mat/MemoryAnalyzer"]
```

```
FROM fedora
RUN yum install -y wget unzip java-1.8.0-openjdk.x86_64 webkitgtk4 firefox
# Download snapshot build, just for testing
RUN wget "http://www.eclipse.org/downloads/download.php?file=/mat/snapshots/rcp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip&mirror_id=1" -O /tmp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip
RUN unzip /tmp/org.eclipse.mat.ui.rcp.MemoryAnalyzer-linux.gtk.x86_64.zip -d /opt
ENV DISPLAY host.docker.internal:0.0
CMD ["/opt/mat/MemoryAnalyzer"]
```