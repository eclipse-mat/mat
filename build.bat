cd trunk

@echo off
if not defined M2_HOME (
	echo variable M2_HOME must be set
	exit 1
)
echo on

REM call ant.bat -file prepare-dep-repos.xml

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

cd prepare_build
set MVN_PREPARE_CALL=%M2_HOME%\bin\mvn.bat %repoOpts% -DproxyHost=%proxyHost% -DproxyPort=%proxyPort% %settingsOpts% clean install
echo %MVN_PREPARE_CALL%
call %MVN_PREPARE_CALL%
set result=%ERRORLEVEL%
cd ..
if not %result%==0 (
	exit /B %result%
)

set MVN_CALL=%M2_HOME%\bin\mvn.bat %repoOpts% %proxyOpts% -fae %settingsOpts% clean install findbugs:findbugs
echo %MVN_CALL%
call %MVN_CALL% 
set result=%ERRORLEVEL%

REM call ant.bat -file prepare-dep-repos.xml cleanup

exit /B %result%
