# Uses only the existing disposable emulator; requires an installed debug APK and fixture.
param([string]$Sdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$root = Split-Path $PSScriptRoot
$report = Join-Path $root '.private\emulator-checks.txt'
function Adb { & $adb -s emulator-5554 @args }
if ((Adb emu avd name) -notcontains 'QuickLens_Pixel_API_34') { throw 'Unexpected AVD.' }
function Images { @(Adb shell run-as local.quicklens ls cache/lens | Where-Object { $_ -match '^[a-f0-9-]+\.jpg$' }) }
function Logs { (Adb logcat -d -s QuickLens:I AndroidRuntime:E '*:S') -join "`n" }
function Check([bool]$condition, [string]$name) {
    if (-not $condition) { throw "FAIL: $name`n$(Logs)" }
    "PASS: $name" | Tee-Object -FilePath $report -Append
}
function Fixture([bool]$secure = $false) {
    Adb shell am force-stop local.quicklens.fixture | Out-Null
    Adb shell am start -W -n local.quicklens.fixture/.TestActivity --ez secure $secure.ToString().ToLowerInvariant() | Out-Null
    Start-Sleep -Milliseconds 500
}
function Tap {
    Adb logcat -c
    Adb shell cmd statusbar expand-settings
    Start-Sleep -Milliseconds 900
    Adb shell cmd statusbar click-tile local.quicklens/.TranslateTile
}
Set-Content $report 'Quick Lens Android 14 emulator checks'
# Keep the test tile on the visible page so System UI binds it before click-tile.
Adb shell "settings put secure sysui_qs_tiles 'custom(local.quicklens/.TranslateTile),internet,bt,flashlight,dnd,alarm,airplane,rotation'"

Fixture
$before = (Images).Count
Tap
Start-Sleep -Seconds 4
Check ((Logs) -match 'lens_launched' -and (Logs) -match 'translate_mode_clicked|translate_already_selected' -and (Images).Count -eq $before + 1) 'External fixture captured; Lens opened and Translate selected'
$first = (Images)[-1]
$hash = (Adb shell run-as local.quicklens sha256sum "cache/lens/$first") -join ''

$before = (Images).Count
Tap
Start-Sleep -Seconds 3
Check ((Logs) -match 'capture_aborted' -and (Logs) -notmatch 'lens_launched' -and (Images).Count -eq $before) 'Lens already open: no recursive capture'

Adb shell am start -W -f 0x10008000 -n local.quicklens/.MainActivity | Out-Null
Start-Sleep -Milliseconds 500
$before = (Images).Count
Tap
Start-Sleep -Seconds 3
Check ((Logs) -match 'capture_aborted' -and (Logs) -notmatch 'lens_launched' -and (Images).Count -eq $before) 'Own setup UI is never shared'

Fixture $true
$before = (Images).Count
Tap
Start-Sleep -Seconds 3
Check ((Logs) -match 'capture_failed code=6' -and (Logs) -notmatch 'lens_launched' -and (Images).Count -eq $before) 'FLAG_SECURE respected; no image shared'

Fixture
$enabledServices = (Adb shell settings get secure enabled_accessibility_services).Trim()
Adb shell settings delete secure enabled_accessibility_services
Start-Sleep -Milliseconds 600
$before = (Images).Count
try {
    Tap
    Start-Sleep -Seconds 3
    Check ((Logs) -notmatch 'capture_requested|lens_launched' -and (Images).Count -eq $before) 'Disconnected Accessibility: no screenshot or Lens launch'
} finally {
    Adb shell settings put secure enabled_accessibility_services $enabledServices
    Adb shell settings put secure accessibility_enabled 1
}
Start-Sleep -Seconds 2

Fixture
$before = (Images).Count
Tap
Start-Sleep -Milliseconds 100
Adb shell cmd statusbar click-tile local.quicklens/.TranslateTile
Adb shell cmd statusbar click-tile local.quicklens/.TranslateTile
Start-Sleep -Seconds 4
Check (([regex]::Matches((Logs), 'lens_launched')).Count -eq 1 -and (Images).Count -eq $before + 1) 'Rapid repeated taps: exactly one image and one launch'
Check (((Adb shell run-as local.quicklens sha256sum "cache/lens/$first") -join '') -eq $hash) 'Earlier Lens image is retained and not overwritten'

Fixture
$before = (Images).Count
Tap
Start-Sleep -Milliseconds 300
Adb shell am start -W -a android.settings.SETTINGS | Out-Null
Start-Sleep -Seconds 3
Check ((Logs) -match 'capture_aborted' -and (Logs) -notmatch 'lens_launched' -and (Images).Count -eq $before) 'Switching apps during capture cancels the operation'

$stale = '00000000-0000-0000-0000-000000000000.jpg'
Adb shell run-as local.quicklens touch "cache/lens/$stale"
Adb shell run-as local.quicklens touch -t 202001010000 "cache/lens/$stale"
$oldPid = (Adb shell pidof local.quicklens).Trim()
Adb shell run-as local.quicklens kill -9 $oldPid
Start-Sleep -Seconds 3
$newPid = (Adb shell pidof local.quicklens).Trim()
Check ($oldPid -ne $newPid -and $newPid.Length -gt 0) 'Android reconnects Accessibility after process death'
Check ((Images) -notcontains $stale -and (Images) -contains $first) 'Startup removes expired interrupted captures and retains recent images'
Fixture
Tap
Start-Sleep -Seconds 4
Check ((Logs) -match 'lens_launched') 'Tile works after process restart'

$denied = (Adb shell content read --uri "content://local.quicklens.image/$first" 2>&1) -join "`n"
Check ($denied -match 'Permission Denial|SecurityException') 'Provider rejects an ungranted reader'
