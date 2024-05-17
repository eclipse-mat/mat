# Building MAT with Maven

This page describes how Memory Analyzer can be built using Maven/Tycho. The build will
* build all MAT bundles
* execute all tests
* (optional) run [SpotBugs](https://spotbugs.github.io/) static checks
* build eclipse features containing the MAT plugins and produce an update site (p2 repository) with them
* produce standalone (Eclipse RCP based) products for different OS platforms
* produce a software bill of materials listing
* sign and upload the produced artifacts (when executed on the Eclipse Jenkins server)

## Prerequisites

### Clone the Source Code from Git
MAT sources are in a Git repository, therefore you need a git client. Have a look at [Contributor_Reference.md#Get the source](Contributor_Reference.md#Get the source)

### Use Java 17 for the Build
Memory Analyzer 1.15 requires Java 17 for the build and tests as it is based on Eclipse 4.30 2021-12, and uses Tycho 4.0.3, even though currently the highest level required to compile the Memory Analyzer plugins is 1.8. It requires Java 17 to run, and this is checked on start up.
Make sure the JAVA_HOME environment variable is set to point to a JDK 17 installation.

Previous versions of Memory Analyzer required the MAT build has to be run with Java 1.8. For those, make sure the JAVA_HOME environment variable is set to point to a JDK 1.8 installation.

### Install and Configure Maven
The Memory Analyzer build requires a Maven 3.9.* installation (3.8 won't work with Tycho 4.0.3). It is already present on the Jenkins server at Eclipse. For local build one can download it from [here](http://maven.apache.org/download.html).

If you need to set a proxy for Maven, a snippet like this can be added to the Maven settings file:

```
  <proxies>
    <proxy>
      <active>true</active>
      <protocol>http</protocol>
      <port>8080</port>
      <host>myproxy_host</host>
      <nonProxyHosts>non_proxy_hosts</nonProxyHosts>
    </proxy>
  </proxies>
```

More information on Maven settings: http://maven.apache.org/ref/3.9.5/maven-settings/settings.html

## Building MAT from Sources

### Execute the build
* Open a console and go into the ''<mat_src>/parent'' folder (it contains the parent pom.xml)
* To build MAT with the default profile (build-snapshot) simply execute
```mvn clean install```
* This will cause a fresh build of all bundles, execute tests, build eclipse features, an update site (p2 repository) and standalone products
* If you want to also FindBugs checks are performed as part of the build, then execute
```mvn clean install spotbugs:spotbugs```

### Where to find the results?
You can find the results of the build in the corresponding .../target/ directories for each plugin, feature, etc... Of particular interest are:
* ''<mat_src>/org.eclipse.mat.updatesite/target/site/'' - it contains a p2 repository with MAT features
* ''<mat_src>/org.eclipse.mat.product/target/products/'' - it contains all standalone RCP applications

## Building MAT Standalone RCPs from an Existing MAT Update Site

### Configure and execute the build
* Open a console and go into the ''<mat_src>/parent'' folder (it contains the parent pom.xml)
* To produce only the standalone products, using an already existing MAT repository (i.e. without building the bundles again) specify that the ''build-release-rcp'' profile is used when you start maven:
```mvn clean install -P build-release-rcp```
* It will take the already existing MAT plugins/features from the repository specified by the ''mat-release-repo-url'' property in ''<mat_src>/parent/pom.xml''. One can overwrite this location when calling maven. For example, to build products with the older 1.5.0 release, use:
```mvn clean install -P build-release-rcp -Dmat-release-repo-url=http://download.eclipse.org/mat/1.5/update-site```

### Where to find the results?
You can find the standalone products under ''<mat_src>/org.eclipse.mat.product/target/products/''

## Further Information
* The platforms for which RCPs are built are specified in the ''<mat_src>/parent/pom.xml'' file

## Known Problems
### Wrong file permissions
When building MAT on a Windows box, the RCPs for any other OS will not have the proper permissions (e.g. the executables won't have the x flag). Building under Linux or other non-Windows OS helps.

## Jenkins Job at Eclipse
The Jenkins continuous integration instance of the Memory Analyzer Project at the Eclipse is https://ci.eclipse.org/mat/.

### Snapshot / Nightly builds
The [''tycho-mat-nightly''](https://ci.eclipse.org/mat/job/tycho-mat-nightly/) job is triggered when changes are pushed to the `master` branch and produces a snapshot build (see Building MAT from Sources) above.

One can find the bill of matterials under [Last build artifacts: bill of materials](https://ci.eclipse.org/mat/job/tycho-mat-nightly/lastSuccessfulBuild/artifact/)

The job is additionally configured to sign the plugins and features in the update site, and to upload all artifacts to the download server.
One can download such nightly/snapshot builds here: http://www.eclipse.org/mat/snapshotBuilds.php

Info: 
* Signing is activated by the build-server profile (i.e. with parameter `-P build-server` added to the maven command)
* The macOS builds are also notarized by the [mac-sign job](https://ci.eclipse.org/mat/job/mac-sign/) (committers only), which is automatically triggered after a successful snapshot build.

### Release Builds
The job [''mat-standalone-packages''](https://ci.eclipse.org/mat/job/mat-standalone-packages/) can only be triggered manually to build the MAT standalone packages/products using an already existing MAT update site. This can be used in the following scenario - MAT has contributed its bundles and features to the simultaneous Eclipse release as part of a milestone or a release candidate. After the simultaneous release is complete, we would like to have exactly these bundles also packed in the standalone packages, potentially including also the final version of the dependencies (part of the same simultaneous release).

The job is configured to use the ''build-release-rcp'' profile when calling maven.

The job may need to be changed for each new release.

After building the packages the macOS build needs to be notarized using the [''mac-sign job''](https://ci.eclipse.org/mat/job/mac-sign/) (committers only), with the parameter of the actual relative location of the dmg file on the download server.

The downloads can then be tested.

The job [''mat-promote-release''](https://ci.eclipse.org/mat/job/mat-promote-release/) (committers only) copies the files to their final location so they can be downloaded by all the users.

The job [''update_latest_update-site''](https://ci.eclipse.org/mat/job/update_latest_update-site/) (committers only) copies a particular release update site to the /mat/latest/update-site

