param([string]$Sdk = $env:ANDROID_HOME, [string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $Sdk -or -not $JavaHome) { throw 'Set ANDROID_HOME and JAVA_HOME first.' }
$root = Split-Path $PSScriptRoot
$output = Join-Path $root '.private\fixture'
$tools = Join-Path $Sdk 'build-tools\34.0.0'
$android = Join-Path $Sdk 'platforms\android-34\android.jar'
New-Item -ItemType Directory -Force "$output\classes", "$output\dex" | Out-Null
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Fixture build failed: $LASTEXITCODE" } }
& "$tools\aapt2.exe" link -o "$output\unsigned.apk" -I $android --manifest "$PSScriptRoot\fixture\AndroidManifest.xml"
Check-Exit
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$output\classes" "$PSScriptRoot\fixture\TestActivity.java"
Check-Exit
& "$JavaHome\bin\jar.exe" cf "$output\classes.jar" -C "$output\classes" .
Check-Exit
& "$JavaHome\bin\java.exe" -cp "$tools\lib\d8.jar" com.android.tools.r8.D8 --min-api 31 --lib $android --output "$output\dex" "$output\classes.jar"
Check-Exit
Copy-Item "$output\dex\classes.dex" "$output\classes.dex" -Force
Push-Location $output
try { & "$tools\aapt.exe" add unsigned.apk classes.dex; Check-Exit } finally { Pop-Location }
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
Check-Exit
$debugKey = Join-Path $env:USERPROFILE '.android\debug.keystore'
& "$tools\apksigner.bat" sign --ks $debugKey --ks-pass pass:android --key-pass pass:android --out "$output\fixture.apk" "$output\aligned.apk"
Check-Exit
Write-Output "Fixture APK: $output\fixture.apk"
