Eclipse Memory Analyzer
====================

The Eclipse Memory Analyzer is a fast and feature-rich Java heap dump analyzer that helps you find memory leaks and reduce memory consumption.

- Web site: <http://eclipse.dev/mat/>

Download
----------------

- Latest [stable release](https://www.eclipse.org/mat/downloads.php)
- [Snapshot builds](https://www.eclipse.org/mat/snapshotBuilds.php)

Building locally
----------------

Memory Analyzer can be build using Maven / Tycho. The maven build should be started from the parent directory. So, clone the repository, and then

    cd parent
    mvn clean install

For detailed build documentation refer to <https://wiki.eclipse.org/MemoryAnalyzer/Building_MAT_With_Tycho>

Documentation
----------------

- [Documentation](http://help.eclipse.org/index.jsp?topic=/org.eclipse.mat.ui.help/welcome.html) - online version of the documentation available also from within the tool
- [Learning](https://wiki.eclipse.org/MemoryAnalyzer/Learning_Material) - learn from presentations, blogs, tutorials
- [FAQ](http://wiki.eclipse.org/index.php/MemoryAnalyzer/FAQ) - check some of the frequently asked questions

Contributing
----------------

- Check [CONTRIBUTING.md](CONTRIBUTING.md) in the root of the repository
- [Developers](https://www.eclipse.org/mat/developers/) area on the MAT web site
- [MemoryAnalyzer/Contributor Reference](http://wiki.eclipse.org/index.php?title=MemoryAnalyzer/Contributor_Reference) Wiki pages

Issues:
----------------

This project uses Githib issues to track ongoing development and issues.

- [List of open issues](https://github.com/eclipse-mat/mat/issues?q=is%3Aopen+is%3Aissue)
- [Report an issue](https://github.com/eclipse-mat/mat/issues/new)

In the past the project used Bugzilla. The "archives" are available [here](https://bugs.eclipse.org/bugs/buglist.cgi?order=changeddate%20DESC%2Cpriority%2Cbug_severity&product=MAT&query_format=advanced)

Contact:
----------------

- Developers [mailing list](https://dev.eclipse.org/mailman/listinfo/mat-dev)
- Memory Analyzer [Forum](http://www.eclipse.org/forums/eclipse.memory-analyzer) at Eclipse
