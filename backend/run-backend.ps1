$javaHome = "C:\Program Files\Android\Android Studio\jbr"
$javaExe = Join-Path $javaHome "bin\java.exe"

if (-not (Test-Path $javaExe)) {
    Write-Error "Não encontrei um Java 17+ em '$javaHome'. Atualiza o caminho em backend/run-backend.ps1."
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

& "..\gradlew.bat" bootRun
exit $LASTEXITCODE