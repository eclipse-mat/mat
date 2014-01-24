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

cd parent
MVN_CALL="$M2_HOME/bin/mvn -Dmaven.repo.local=../../.repository $proxyOpts $mvnArguments clean install $additionalPlugins"
echo $MVN_CALL
eval $MVN_CALL
result=$?
echo result=$result
cd ..
if [ $result != 0 ]; then
	exit $result
fi

cd parent
ant -file copy-build-results.xml
result=$?
echo result=$result
cd ..

exit $result
