README

This project is a template which defines minimal 
one or more map files, build.xml, build.properties

Additional Optional files:
buildExtra.xml (overrides to generic build.xml)
testing.properties, testExtra.xml

allElements.xml
customTargets.xml
customAssembly.xml
myproduct.product (not yet supported)
pack.properties (to disable packing for some plugins)
promote.properties

You may find that copying from a working example is more meaningful than using the sample "foo" project here. See links below.

---------------------------------------------------------------------------

Gotchas:

You must ensure your plugins set Bundle-RequireExecutionEnvironment (BREE) correctly, since the build will NOT.

---------------------------------------------------------------------------

Latest documentation:

http://wiki.eclipse.org/Common_Build_Infrastructure/Getting_Started/Build_In_Eclipse
http://wiki.eclipse.org/Common_Build_Infrastructure/Getting_Started/FAQ
http://wiki.eclipse.org/Category:Athena_Common_Build
http://wiki.eclipse.org/Common_Build_Infrastructure/Defining_Binary_Dependencies

---------------------------------------------------------------------------

Sample projects from which to copy:

CVS:

cvs -d :pserver:anonymous@dev.eclipse.org:/cvsroot/technology -q co org.eclipse.dash/athena/org.eclipse.dash.commonbuilder/*.releng

SVN:

svn -q co http://dev.eclipse.org/svnroot/technology/org.eclipse.linuxtools/releng/trunk/org.eclipse.linuxtools.releng/
svn -q co http://anonsvn.jboss.org/repos/jbosstools/trunk/jmx/releng/
svn -q co http://anonsvn.jboss.org/repos/jbosstools/trunk/bpel/releng/
svn -q co http://anonsvn.jboss.org/repos/jbosstools/trunk/jbpm/releng/

Sample Hudson configuration scripts:

https://build.eclipse.org/hudson/view/Athena%20CBI/
