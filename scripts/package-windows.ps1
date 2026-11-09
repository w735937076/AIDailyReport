# Build Windows installer / app image with jpackage (run on Windows).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "==> Maven package"
mvn -q -DskipTests package

$Jar = Join-Path $Root "target\dailyreport-1.0.0.jar"
$Out = Join-Path $Root "dist\win"

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path (Join-Path $Root "dist") | Out-Null

if (Get-Command jpackage -ErrorAction SilentlyContinue) {
    jpackage `
        --input (Join-Path $Root "target") `
        --name "AIDailyReport" `
        --main-jar "dailyreport-1.0.0.jar" `
        --main-class org.springframework.boot.loader.launch.JarLauncher `
        --type exe `
        --dest $Out `
        --app-version 1.0.0 `
        --description "Git commits to AI-enhanced daily reports" `
        --vendor "AIDailyReport" `
        --win-menu `
        --win-shortcut `
        --java-options "-Dfile.encoding=UTF-8" `
        --java-options "-Dapp.open-browser=true"
    Write-Host "Installer / app image in $Out"
} else {
    Write-Host "jpackage not in PATH; use: java -jar $Jar"
}
