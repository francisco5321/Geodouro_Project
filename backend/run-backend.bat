@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Não encontrei um Java 17+ em "%JAVA_HOME%".
    echo Atualiza o caminho em backend\run-backend.bat.
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
call ..\gradlew.bat bootRun
exit /b %ERRORLEVEL%