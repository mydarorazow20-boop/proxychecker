#!/bin/sh
# STOPGAP launcher, not the official Gradle script.
# gradle/wrapper/gradle-wrapper.jar could not be downloaded when this ZIP was
# produced (no network). Run once, with network access:
#   sh tools/bootstrap-wrapper.sh
# It downloads Gradle, verifies the published SHA-256, runs `gradle wrapper`, and
# REPLACES this file, gradlew.bat and the properties with the official ones.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: $WRAPPER_JAR is missing. Run: sh tools/bootstrap-wrapper.sh  (see README.md, 'Gradle Wrapper')." >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
