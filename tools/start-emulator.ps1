param([switch]$ColdBoot)
$ErrorActionPreference = 'Stop'
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adbExe = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdkRoot 'emulator\emulator.exe'
$avdName = 'QuickLens_Pixel_API_34'
$env:ANDROID_HOME = $sdkRoot
$env:Path = "$(Join-Path $sdkRoot 'platform-tools');$env:Path"
if (-not (Test-Path $emulatorExe)) { throw "Android Emulator is missing: $emulatorExe" }
$devices = & $adbExe devices
foreach ($line in $devices) {
    if ($line -match '^(emulator-\d+)\s+device$') {
        $serial = $Matches[1]
        $name = & $adbExe -s $serial emu avd name
        if ($name -contains $avdName) { Write-Output "Already running: $avdName ($serial)"; exit 0 }
    }
}
if ($devices -match '^emulator-5554\s') { throw 'Emulator port 5554 is already occupied by another AVD.' }
$logDirectory = Join-Path $env:LOCALAPPDATA 'Android\emulator-logs'
New-Item -ItemType Directory -Force $logDirectory | Out-Null
$arguments = @('-avd', $avdName, '-port', '5554', '-no-boot-anim', '-adb-path', $adbExe)
if ($ColdBoot) { $arguments += '-no-snapshot-load' }
Start-Process -FilePath $emulatorExe -ArgumentList $arguments -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDirectory 'quicklens-output.log') `
    -RedirectStandardError (Join-Path $logDirectory 'quicklens-error.log') | Out-Null
Write-Output "Starting $avdName. ADB target: emulator-5554. Emulator opens its own graphical window."
