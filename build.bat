cd trunk

@echo off
if not defined M2_HOME (
	echo variable M2_HOME must be set
	exit 1
)
echo on

call ant.bat -file prepare-dep-repos.xml

if defined proxyHost (
	if defined nonProxyHosts (
		set proxyOpts=-DproxySet=true -Dhttp.proxyHost=%proxyHost% -Dhttp.proxyPort=%proxyPort% -Dhttp.nonProxyHosts=%nonProxyHosts%
	) else (
		set proxyOpts=-DproxySet=true -Dhttp.proxyHost=%proxyHost% -Dhttp.proxyPort=%proxyPort%
	)
)
set MVN_CALL=%M2_HOME%\bin\mvn.bat -Dmaven.repo.local=..\.repository %proxyOpts% -fae -s settings.xml clean install findbugs:findbugs
echo %MVN_CALL%
call %MVN_CALL% 
set result=%ERRORLEVEL%

call ant.bat -file prepare-dep-repos.xml cleanup

exit %result%
