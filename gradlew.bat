@rem STOPGAP launcher, not the official Gradle script. The wrapper JAR is missing from this ZIP.
@rem Generate the official wrapper with: gradle wrapper --gradle-version 8.9 --distribution-type bin
@rem (or run tools/bootstrap-wrapper.sh from Git Bash/WSL). See README.md, "Gradle Wrapper".
@echo off
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo ERROR: %WRAPPER_JAR% is missing. Run "gradle wrapper --gradle-version 8.9 --distribution-type bin" (see README.md). 1>&2
  exit /b 1
)
if defined JAVA_HOME (set JAVACMD=%JAVA_HOME%\bin\java.exe) else (set JAVACMD=java.exe)
"%JAVACMD%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
