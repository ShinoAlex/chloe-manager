$ErrorActionPreference = "Stop"
$out = "c:\Users\lolre\OneDrive\Desktop\Project\java-project\chloe-manager\build-output.txt"
$javaHome = "C:\Program Files\Java\jdk-17"
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

$mvnDir = "$env:TEMP\apache-maven-3.9.9"
$mvnZip = "$env:TEMP\apache-maven-3.9.9-bin.zip"

# Download Maven if not present
if (-not (Test-Path "$mvnDir\bin\mvn.cmd")) {
    Write-Output "Downloading Maven..."
    if (-not (Test-Path $mvnZip) -or (Get-Item $mvnZip).Length -lt 100000) {
        try {
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile("https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip", $mvnZip)
        } catch {
            Write-Output "Download failed: $_"
            # Try alternate URL
            try {
                $wc2 = New-Object System.Net.WebClient
                $wc2.DownloadFile("https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip", $mvnZip)
            } catch {
                Write-Output "Alternate download also failed: $_"
                exit 1
            }
        }
    }
    Write-Output "Extracting Maven..."
    Expand-Archive -Path $mvnZip -DestinationPath $env:TEMP -Force
}

$mvn = "$mvnDir\bin\mvn.cmd"
Write-Output "Maven found: $(Test-Path $mvn)"
Write-Output "Java version:"
& "$javaHome\bin\java.exe" -version 2>&1 | Out-String

Write-Output "Starting build..."
Set-Location "c:\Users\lolre\OneDrive\Desktop\Project\java-project\chloe-manager"
& $mvn clean compile -B 2>&1 | Tee-Object -FilePath $out
