cd trunk

@echo off
if not defined M2_HOME (
	echo variable M2_HOME must be set
	exit 1
)
echo on

if defined proxyHost (
	if defined nonProxyHosts (
		set proxyOpts=-DproxySet=true -Dhttp.proxyHost=%proxyHost% -Dhttp.proxyPort=%proxyPort% -Dhttp.nonProxyHosts=%nonProxyHosts%
	) else (
		set proxyOpts=-DproxySet=true -Dhttp.proxyHost=%proxyHost% -Dhttp.proxyPort=%proxyPort%
	)
)

if defined mvnSettings (
	set settingsOpts=-s %mvnSettings%
)
if defined mvnRepository (
	set repoOpts=-Dmaven.repo.local=%mvnRepository%
)

cd parent
set MVN_CALL=%M2_HOME%\bin\mvn.bat %repoOpts% %proxyOpts% -fae %settingsOpts% clean install findbugs:findbugs
echo %MVN_CALL%
call %MVN_CALL% 
set result=%ERRORLEVEL%
cd ..

exit /B %result%
