$ErrorActionPreference = "Stop"

# ── Toolchain paths (this machine) ──────────────────────────────────────────
$javaHome = "C:\Program Files\Java\jdk-25"
$mvn      = "D:\Tool\apache-maven-3.10.2\bin\mvn.cmd"
$project  = "d:\GAMEMODS\MC\server\chloe-manager"
$out      = Join-Path $project "build-output.txt"

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

if (-not (Test-Path $mvn)) {
    Write-Output "Maven not found at: $mvn"
    Write-Output "Expected: D:\Tool\apache-maven-3.10.2\bin\mvn.cmd"
    exit 1
}

Write-Output "Java:"
& "$javaHome\bin\java.exe" -version 2>&1 | Out-String
Write-Output "Maven: $mvn"

Set-Location $project
& $mvn clean package -B 2>&1 | Tee-Object -FilePath $out
Write-Output ""
Write-Output "Built jar:"
Get-ChildItem (Join-Path $project "target\*.jar") |
    Where-Object { $_.Name -notlike "original-*" } |
    Select-Object Name, Length, LastWriteTime | Format-List
