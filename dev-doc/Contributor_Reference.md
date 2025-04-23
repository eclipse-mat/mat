# Memory Analyzer Contributor Reference

This page is meant to help you contribute to the Memory Analyzer project.

## Workspace

Here you'll find instructions on how to setup your development environment for developing MAT using Eclipse as IDE

### Get the source

The Memory Analyzer code is hosted on Github:

https://github.com/eclipse-mat/mat

### Setup Eclipse as IDE

You need a recent Eclipse installation. Memory Analyzer is a set of eclipse plugins,= therefore you'll need the appropriate tooling for plugin development. The [''Eclipse IDE for Eclipse Committers''](https://www.eclipse.org/downloads/packages/) is an appropriate package.

1. Clone MAT source:
   ```
   git clone https://github.com/eclipse-mat/mat
   ```
2. Import projects: File } Import... } General } Existing Projects into Workspace } Select your cloned MAT source directory
    * Importing Existing Projects is [preferred](https://www.eclipse.org/lists/mat-dev/msg00788.html) over Existing Maven Projects
3. Dependencies: Choose one of the following options to be able to compile MAT:
    1. The easiest way to setup all dependencies is to use a target platform definition file, which can be found in org.eclipse.mat.targetdef. Open the most recent one with the Target Definition Editor and select ''Set as Active Target Platform''. After this, all projects should compile.
    2. Alternatively, you'll need to install some plugins using the update manager:
        * Eclipse [BIRT Framework](https://download.eclipse.org/birt/update-site/latest/): BIRT Charting SDK and BIRT Reporting SDK
        * [IBM Diagnostic Tool Framework for Java](https://public.dhe.ibm.com/ibmdl/export/pub/software/websphere/runtimes/tools/dtfj/). For details, see [Diagnostic Tool Framework for Java](https://www.ibm.com/docs/en/sdk-java-technology/8?topic=interfaces-dtfj). This is needed to compile and run with the DTFJ adapter which is part of Memory Analyzer and allows Memory Analyzer to read dumps from IBM virtual machines for Java.
        * [SWTBot](https://download.eclipse.org/technology/swtbot/releases/latest/): SWTBot - API } SWTBot for Eclipse Testing

If you do not have BIRT installed then there will be compilation errors in the org.eclipse.mat.chart and org.eclipse.mat.chart.ui projects.

If you do not have the IBM DTFJ feature installed then there will be compilation errors in the org.eclipse.mat.dtfj project.

### Configure the Code Formatter Template

The MAT code is formatted with a specific code formatter. Use it if you would like to contribute you changes to MAT.

''Preferences -&gt; Java -&gt; Code Style -&gt; Formatter -&gt; Import...'' and import this [template](code_formatter/mat_code_formatter.xml).

### Configure API Tooling Baseline

In order to guarantee that no API breaking changes are introduced we recommend using the PDE API Tooling and defining the latest released version of MAT as an API Baseline. Here is a short description how this could be done:

* Download the latest released version in order to use it as an API Baseline
  * Go to the [MAT download page](https://eclipse.dev/mat/download/)
  * Download the &quot;Archived Update Site&quot; zip file for the latest release
  * Unzip the file somewhere locally
* Configure the API Baseline in the IDE
  * In the IDE open '' Window -&gt; Preferences -&gt; Plug-in Development -&gt; API Baselines ''
  * Press ''Add Baseline''
  * Select ''An Existing Eclipse installation Directory'' as the source for this baseline.
  * Browse and select as ''Location'' the directory in which the zip was extracted
  * Enter a name for the baseline, click ''Finish'' and confirm the rest of the dialogs

Once the API Tooling is properly setup, one will see errors reported if API changes are introduced.

### Launch Configuration

There are different ways to launch MAT from within an Eclipse development environment:

1. Open Run } Run Configurations...
2. Click Eclipse Application
3. Click New launch configuration
4. In the Name: textbox, enter MAT
5. Then choose one of the following approaches:
    1. Launch the Memory Analyzer as '''stand-alone RCP''':
        * Create a new ''Eclipse Application'' configuration
        * Run a product: ''org.eclipse.mat.ui.rcp.MemoryAnalyzer''
        * Launch with: ''plug-ins selected below only''
            * Deselect ''org.eclipse.mat.tests'', ''org.eclipse.mat.ui.rcp.tests'', ''org.eclipse.mat.ui.capabilities''
            * Deselect ''Target Platform'' and click ''Select Required'' (previously ''Add Required Plug-ins'')
            * With Eclipse 2024-03 or later, this is all you need to do. With older Eclipse versions, you'll need to manually select a few more plugins
              * Select ''org.eclipse.pde.runtime'' (3.3) or ''org.eclipse.ui.views.log'' (3.4 or later) to include the Error Log
              * Select ''com.ibm.dtfj.api'' ''com.ibm.dtfj.j9'' ''com.ibm.dtfj.phd'' ''com.ibm.dtfj.sov'' if you have installed the IBM DTFJ feature and wish to process dumps from IBM virtual machines
              * Select ''com.ibm.java.doc.tools.dtfj'' for help for IBM DTFJ
              * Eclipse &gt;= Neon: Select ''org.eclipse.equinox.ds'' and ''org.eclipse.equinox.event''
    2. As '''feature plugged into the IDE''':
        * Create a new ''Eclipse Application'' configuration
        * Run a product: ''org.eclipse.sdk.ide''
        * Launch with: ''plug-ins selected below only''
            * De-select ''org.eclipse.mat.tests'', ''org.eclipse.mat.ui.rcp.tests'', ''org.eclipse.mat.ui.capabilities'' and ''org.eclipse.mat.ui.rcp''
            * Select ''com.ibm.dtfj.api'' ''com.ibm.dtfj.j9'' ''com.ibm.dtfj.phd'' ''com.ibm.dtfj.sov'' if you have installed the IBM DTFJ feature and wish to process dumps from IBM virtual machines
            * Select ''com.ibm.java.doc.tools.dtfj'' for help for IBM DTFJ
            * Eclipse &gt;= Neon: Select ''org.eclipse.equinox.ds'' and ''org.eclipse.equinox.event''
            * Eclipse &gt;= Oxygen: Select ''org.eclipse.equinox.event''

If parsing IBM Java dumps, add the following JVM argument under Arguments } VM Arguments: `--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED`

### Create a Stand-Alone RCP

See [Building MAT with Maven](Building_MAT_with_Maven.md) if you want to produce a standalone MAT.

### JUnit Tests

The unit tests a placed in the ''org.eclipse.mat.tests'' project. Execute the tests by right-clicking on the project and choose ''Run As... -&gt; JUnit Plug-in Test''.

The following VM arguments are required in the run configuration for the JUnit Plug-in Test: 
```-Xmx850m -ea --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED```

For the ''org.eclipse.mat.rcp.tests'' project install SWTBot - API from [https://www.eclipse.org/swtbot/].

### Build Help with DITA

* Download [DITA-OT 3.7](https://github.com/dita-ot/dita-ot/releases/download/3.7/dita-ot-3.7.zip) and unzip it into somewhere on your disk, e.g. C:\dita-ot-3.7. Please stick to this DITA version, it is the one with which the help pages are currently built. Using a different version results in many unnecessary file changes to the generated files (which are also committed in the git repository).

* In plugin '''org.eclipse.mat.ui.help''' select '''DitaBuild.xml''' and configure the runtime configuration:
  * right click ''Run As &gt; Ant Build...''
  * Refresh &gt; Refresh resources upon completion. &gt; The project containing the selected resource
  * configure the DITA directory and libraries:
    * add property dita.dir (this overrides the version in DitaBuild.xml)
     * Properties
     * Add Property
     * Variables
     * Edit Variables
     * New
      * Name: dita.dir
      * Value: the location of DITA, e.g. C:\dita-ot-3.7
      * OK
  * Alternatively to run DITA-OT from the command line
    * Set the dita directory variable, e.g. &lt;code&gt;set DITA_DIR=C:\dita-ot-3.7&lt;/code&gt;
    * Add DITA to the path, e.g. &lt;code&gt;set PATH=%DITA_DIR%\bin;%PATH%&lt;/code&gt;
    * change to the org.eclipse.mat.ui.help directory and run one of the following:
      * &lt;code&gt;ant -f DitaBuild.xml&lt;/code&gt; [attempts to not change HTML files which have no content changes]
      * &lt;code&gt;ant -f DitaBuild.xml  -Djustnew=true&lt;/code&gt; [attempts to not change HTML files which have no content changes]
      * &lt;code&gt;ant -f DitaBuild.xml  -Djustnew=false&lt;/code&gt; [HTML files are as they come from DITA build, some HTML files may be changed which have no content changes]
* To modify Help documentation modify xml files
  * XML Buddy - might not be available anymore
    * [Download XMLBuddy](http://www.xmlbuddy.com) and copy a product directory (e.g., com.objfac.xmleditor_2.0_72) to the plugins directory of your Eclipse installation.
    * Configure XMLBuddy editor as described [here](http://www.ditainfocenter.com/eclipsehelp/index.jsp?topic=/ditaotug_top/settingup/configuring_xmlbuddy.html)
  * or use the XML editor from Eclipse Web Tools
    * Window &gt; Preferences &gt; XML &gt; XML files &gt; Validation &gt; Enable markup validation
    * Window &gt; Preferences &gt; Validator &gt; XML Validator &gt; Settings &gt; Include Group &gt; Add Rule &gt; File extensions : dita
    * Window &gt; Preferences &gt; XML &gt; XML Catalog &gt; User supplied entries &gt; Add XML Catalog Element &gt; Delegate Catalog
      * Key type to match: URI
      * Matching start string: -//OASIS//DTD DITA
      * Delegate to this XML catalog file: %DITA_DIR%/plugins/org.oasis-open.dita.v1_3/catalog.xml
      * [substitute %DITA_DIR% with the actual path]
    * Note that the validation does not seem to work with Eclipse 2022-03 any more - some previous versions did work.
  * or or use the XML editor from Eclipse Web Tools and [Vex](https://projects.eclipse.org/projects/mylyn.docs.vex)
    * It may be easier to still use the XML Editor, as the Vex editor deliberately doesn't show tags, but Vex provides DTD files for DITA, making it possible for XML validation and content assist for DITA files
* Run ant on DitaBuild.xml to build html files.

Note: You may receive the following errors in the Ant build:

```
     [exec] XML utils not found from Ant project reference
     [exec] Store not found from Ant project reference
```

For our usage, these errors are benign; however, if you're not seeing an HTML file for a newly added DITA file, make sure you've added it to `plugins/org.eclipse.mat.ui.help/toc.ditamap` and `plugins/org.eclipse.mat.ui.help/toc.xml`.

### Build OQL Parser using JavaCC

* Download [JavaCC 5.0 tar.gz](https://javacc.org/downloads/javacc-5.0.tar.gz) or [JavaCC 5.0 zip](https://javacc.org/downloads/javacc-5.0.zip) and unpack it.
* Copy javacc.jar to the root of the '''org.eclipse.mat.parser''' project
* In plugin '''org.eclipse.mat.parser''' select '''build_javacc.xml'''
  * right click ''Run As &gt; Ant Build...''
* Select package '''org.eclipse.mat.parser.internal.oql.parser'''
  * Source &gt; Organize Imports
  * Source &gt; Format
  * Ignore the choice conflict message and non-ASCII character message
  * Synchronize with the source repository to add the copyright header etc. back in

### Creating and editing icons

Consider using [EcliPaint](https://marketplace.eclipse.org/content/eclipaint).

For Mac, consider using [icnsutil from PyPI](https://pypi.org/project/icnsutil/) to help build the icns file.

```
#!/bin/sh
cp memory_analyzer_16.png icon_16x16.png
cp memory_analyzer_32.png icon_32x32.png
cp memory_analyzer_48.png icon_48x48.png
cp memory_analyzer_64.png icon_64x64.png
cp memory_analyzer_128.png icon_128x128.png
cp memory_analyzer_256.png icon_256x256.png
icnsutil convert icon_16x16.argb icon_16x16.png
icnsutil convert icon_32x32.argb icon_32x32.png
cp icon_32x32.png icon_16x16@2x.png
cp icon_48x48.png icon_24x24@2x.png
cp icon_64x64.png icon_32x32@2x.png
cp icon_128x128.png icon_64x64@2x.png
cp icon_256x256.png icon_128x128@2x.png
icnsutil c memory_analyzer.icns icon_16x16.argb icon_16x16@2x.png icon_24x24@2x.png icon_32x32.argb icon_32x32@2x.png icon_48x48.png icon_128x128.png icon_128x128@2x.png icon_256x256.png --toc
icnsutil i memory_analyzer.icns
rm icon_*
```

Also see how the icons look in high-contrast mode. See [Bug 342543 Icon decorators not visible in high contrast mode](https://bugs.eclipse.org/bugs/show_bug.cgi?id=342543)
Also consider dark theme: Window &gt; Preferences &gt; General &gt; Appearance &gt; Theme

## Building MAT with Maven/Tycho

The following page describes how Memory Analyzer (p2 repository and standalone RCP applications) can be build using Maven/Tycho: [Building MAT With Maven](Building_MAT_with_Maven.md)

## Testing using Docker
It is possible to [run Memory Analyzer in a Docker container](MAT_in_Docker.md), which might allow testing on different Linux distributions

## Testing using Windows Subsystem for Linux
It is possible to [run Memory Analyzer under WSL](MAT_under_WSL.md), which might allow testing of a Linux distributions when running Windows.

## Contributing code

See [CONTRIBUTING.md](../CONTRIBUTING.md)

## New version development process

* Document new version at [MAT project page](https://projects.eclipse.org/projects/tools.mat)
* Update references to old release in the code e.g. 1.X -&gt; 1.Y excluding update sites
** See pom.xml e.g. `<version>1.9.1-SNAPSHOT</version>`
** See manifest.mf e.g. `Bundle-Version: 1.9.1.qualifier`
** See feature.xml, excluding updateSiteName
** See org.eclipse.mat.ui.rcp about.mappings
** org.eclipse.mat.product mat.product
** org.eclipse.mat.ui.rcp.feature rootfiles/.eclipseproduct (hidden file, may need to use navigator view)

* Develop features and fix bugs
* If a plugin depends on new function in another plugin, update the dependency version in manifest.mf
* If creating a new plugin, add it to the JavaDoc build process in extrabuild.xml, use package-info.java to mark packages as not API or API as appropriate. Consider carefully adding new APIs.
* If the Java version changes then the minor version must increase, also change:
  * .classpath
  * .settings/org.eclipse.jdt.core.prefs
  * manifest.mf &lt;code&gt;Bundle-RequiredExecutionEnvironment: J2SE-1.5&lt;/code&gt;
  * Update org.eclipse.ui.help extrabuild.xml for the new JavaDoc compile level, and for the link to the Java class library documentation
  * Consider keeping org.eclipse.mat.ibmdumps at a lower level as it uses classes for the exec dump provider and the attach jar which may be executed on lower level JVMs.
* Update copyright date in source code if updated in a new year
* If the RCP is to be built against a newer version of Eclipse, then:
  * create a new target platform in org.eclipse.mat.targetdef
  * update org.eclipse.mat.ui.help extrabuild.xml to add a link to the Eclipse help for the platform
  * create a new /org.eclipse.mat.product/mat-&lt;Eclipse-rel&gt;.product file - normally use the same basename as for the target
  * create a new /org.eclipse.mat.product/mat-&lt;Eclipse-rel&gt;.p2.inf file - normally use the same basename as for the target
* Check for regressions/changes in report outputs using the regression test suite
  * Check out the previous release from Git
  * Get it compiled - may need to change target platform
  * In org.eclipse.mat.test, run the `org.eclipse.mat.tests.application` with `-regression ./dumps "-Xmx500m -DMAT_HPROF_DUMP_NR=#1 --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED`. This will run the tests, and establish a baseline if one does not exist
  * Switch back to master
  * Rerun the test which will detect any changes. Examine report.xml to understand whether the changes are expected.
* Towards the end, change the update site references
* Update copyright date in feature.properties etc. if feature/plugin updated in a new year
  * See feature.xml, including updateSiteName
* Also write a New and Noteworthy document replacing the previous release, and add a link to the old document on the MAT website. This should be done in org.eclipse.mat.help in noteworthy.dita, and take the generated noteworthy.html, modify it if needed and add to the website.
* Follow [Simultaneous release policies](#Simultaneous release policies)
* After release create Bugzilla entry for the new version [Bugzilla Manager](https://dev.eclipse.org/committers/bugs/bugz_manager.php)
* [Add a new Babel definition](https://wiki.eclipse.org/Babel/FAQ#How_do_I_add_my_project_to_Babel.3F) so the messages files could be translated.


## Simultaneous release policies

* Create a release record in [Eclipse Memory Analyzer project page](https://projects.eclipse.org/projects/tools.mat)
* For reference, read [contribute to the Simultaneous Release Build](https://github.com/orgs/eclipse-simrel/discussions/3)
* [Build](https://ci.eclipse.org/mat/job/prepare_simrel_contribution/) to copy update site build to SimRel location.
* Follow the SimRel process to update mat.aggrcon in the SimRel build.
* Preserve the [tycho-build-nightly](https://ci.eclipse.org/mat/job/tycho-mat-nightly/) as 'Keep forever' and label it with the release build e.g. 'Photon RC2'
* The [MAT configuration file](http://git.eclipse.org/c/simrel/org.eclipse.simrel.build.git/tree/mat.aggrcon) which is [updated using Git in GitHub.com/eclipse-simrel](https://github.com/orgs/eclipse-simrel/discussions/3) to match the SimRel location.
* Tag in Git with 'R_1.X.Y' the source used to generate the final build for a release. See [MAT Git Refs](https://git.eclipse.org/c/mat/org.eclipse.mat.git/refs/)
* Complete the Eclipse release process, including getting a review if needed at [Eclipse Memory Analyzer project page](https://projects.eclipse.org/projects/tools.mat)
* To release, also run the [Stand-alone packaging build](https://ci.eclipse.org/mat/job/mat-standalone-packages/), notarize the Mac x86_64 and aarch64 .dmg files and copy the results to the download site using [promote release](https://ci.eclipse.org/mat/job/mat-promote-release/).
* Add the version name '1.XX' to [Bugzilla manager](https://dev.eclipse.org/committers/bugs/bugz_manager.php) so users can report bugs against the new version
* Also release on [Eclipse Marketplace](https://marketplace.eclipse.org/content/memory-analyzer-0)
* Also consider archiving some old releases


MAT Policies - to satisfy [Simultaneous Release Requirements](https://github.com/eclipse-simrel/.github/blob/main/wiki/SimRel/Simultaneous_Release_Requirements.md)
* [Ramp Down Plan](https://wiki.eclipse.org/MemoryAnalyzer/Ramp_Down_Plan)
* [Retention Policy](https://wiki.eclipse.org/MemoryAnalyzer/Retention_policy)
* [API Policy](https://wiki.eclipse.org/MemoryAnalyzer/API_policy)
* [Capabilities](https://wiki.eclipse.org/MemoryAnalyzer/MAT_Capabilities)

## Download Statistics

Eclipse committers once logged in at accounts.eclipse.org can see download statistics at
[Download Stats](https://dev.eclipse.org/committers/committertools/stats.php|Eclipse).
These are from downloads via the Find a Mirror script for stand-alone MAT and from p2 downloads from an update site.
Search for '/mat/' for mirror downloads and 'org.eclipse.mat.api' for p2 downloads.
