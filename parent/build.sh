#!/bin/sh
cd trunk

if [ "x$M2_HOME" == "x" ]; then
	echo variable M2_HOME must be set
	exit 1
fi

if [ "x$proxyHost" != "x" ]; then
	proxyOpts="-DproxySet=true -Dhttp.proxyHost=$proxyHost -Dhttp.proxyPort=$proxyPort"
	if [ "x$nonProxyHosts" != "x" ] ; then
		proxyOpts="$proxyOpts -Dhttp.nonProxyHosts=$nonProxyHosts"
	fi
fi

if [ "x$mvnSettings" != "x" ]; then
	settingsOpts="-s $mvnSettings"
fi

cd prepare_build
MVN_PREPARE_CALL="$M2_HOME/bin/mvn -Dmaven.repo.local=../../.repoPrepare $ANT_OPTS -DproxyHost=$proxyHost -DproxyPort=$proxyPort $settingsOpts clean install"
echo $MVN_PREPARE_CALL
eval $MVN_PREPARE_CALL
set result=$?
cd ..
if [ "x$result" != "x" ]; then
	exit $result
fi

cd parent
MVN_CALL="$M2_HOME/bin/mvn -Dmaven.repo.local=../../.repository $proxyOpts $mvnArguments clean install $additionalPlugins"
echo $MVN_CALL
eval $MVN_CALL


set result=$?

echo result=$result
if [ "x$result" == "x" ]; then
	ant -file copy-build-results.xml
fi

cd ..
exit $result
