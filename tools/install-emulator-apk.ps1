param([string]$ApkPath = (Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'))
$ErrorActionPreference = 'Stop'
$adbExe = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = 'emulator-5554'
$avdName = & $adbExe -s $serial emu avd name
if ($LASTEXITCODE -ne 0 -or $avdName -notcontains 'QuickLens_Pixel_API_34') {
    throw 'Expected QuickLens_Pixel_API_34 is not running on emulator-5554.'
}
& $adbExe -s $serial install -r (Resolve-Path -LiteralPath $ApkPath).Path
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
