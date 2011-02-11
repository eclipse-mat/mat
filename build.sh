#!/bin/sh
cd trunk

if [ "x$M2_HOME" == "x" ]; then
	echo variable M2_HOME must be set
	exit 1
fi

ant -file prepare-dep-repos.xml

if [ "x$proxyHost" != "x" ]; then
	proxyOpts="-DproxySet=true -Dhttp.proxyHost=$proxyHost -Dhttp.proxyPort=$proxyPort"
	if [ "x$nonProxyHosts" != "x" ] ; then
		proxyOpts="$proxyOpts -Dhttp.nonProxyHosts=$nonProxyHosts"
	fi
fi

if [ "x$mvnSettings" != "x" ]; then
	settingsOpts="-s $mvnSettings"
fi

MVN_CALL="$M2_HOME/bin/mvn -Dmaven.repo.local=../.repository $proxyOpts -fae $mvnSettings clean install $additionalPlugins"
echo $MVN_CALL
eval $MVN_CALL

set result=$?

ant -file prepare-dep-repos.xml cleanup

echo result=$result
if [ "x$result" == "x" ]; then
	ant -file copy-build-results.xml
fi

exit $result
