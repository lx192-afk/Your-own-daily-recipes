# Builds a Windows installer: target\installer\Daily Menu-<version>.msi
# Needs JDK 17+ (jpackage), Maven, and WiX Toolset 3.x (candle.exe / light.exe).
#
#   powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1 [-WixDir C:\path\to\wix]
param(
    [string]$WixDir = $env:WIX_DIR
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($WixDir) { $env:Path = "$WixDir;$env:Path" }
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset 3.x not found. Install it or pass -WixDir <folder with candle.exe>."
}

$version = ([xml](Get-Content pom.xml)).project.version
mvn -q -B clean javafx:jlink
if ($LASTEXITCODE -ne 0) { throw "jlink build failed" }

jpackage --type msi `
    --name "Daily Menu" `
    --app-version $version `
    --vendor "Daily Menu" `
    --description "Rutgers dining hall menus, nearby restaurants and meal planning" `
    --runtime-image target\DailyMenu `
    --module com.mealmenu/com.mealmenu.Main `
    --icon packaging\daily-menu.ico `
    --win-menu --win-menu-group "Daily Menu" `
    --win-shortcut --win-dir-chooser --win-per-user-install `
    --win-upgrade-uuid 6f2b8f8e-7d0a-4c55-9a43-2f3c1b7f5e21 `
    --dest target\installer
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
Get-ChildItem target\installer\*.msi | ForEach-Object { "Built $($_.FullName) ($([math]::Round($_.Length / 1MB, 1)) MB)" }
