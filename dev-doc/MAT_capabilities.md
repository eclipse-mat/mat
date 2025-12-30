NOTE this page is here for archival purposes only.

# MAT Capabilities

This document provides some sample [capability definitions](https://wiki.eclipse.org/Eclipse/Capabilities)
for the Memory Analyzer (MAT). It describes:

1. Where to find the existing Capabilities plug-in in SVN
2. Or how to implement your own Capabilities for Memory Analyzer

## Existing Capabilities Plug-in

The plug-in `org.eclipse.mat.ui.capabilities` contains Capabilities definition
for Memory Analyzer.

The plug-in can be found in the project repo at:

`plugins/org.eclipse.mat.ui.capabilities`

[Link](https://github.com/eclipse-mat/mat/tree/master/plugins/org.eclipse.mat.ui.capabilities)

## Capabilities Implementation

The code snippet below shows how to turn off Memory Analyzer functionality in
the workbench via Capabilities:

```xml
<extension point="org.eclipse.ui.activities">
	<activity
		id="org.eclipse.mat"
		name="%activity.name"
		description="%activity.description" />

	<activityPatternBinding
		activityId="org.eclipse.mat"
		pattern="org\.eclipse\.mat\..*"/>
   </extension>
```
