#!/bin/sh
# Lanceur Gradle minimal : telecharge et execute la version de Gradle
# declaree dans gradle/wrapper/gradle-wrapper.properties.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAVACMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
fi
exec "$JAVACMD" $JAVA_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
