@rem Lanceur Gradle minimal pour Windows.
@echo off
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" (set JAVACMD=java) else (set JAVACMD=%JAVA_HOME%\bin\java.exe)
"%JAVACMD%" -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
