$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = "$env:LOCALAPPDATA/Android/Sdk"
$jdk = 'C:/Program Files/Android/Android Studio/jbr'
$bt = (Get-ChildItem "$sdk/build-tools" -Directory | Sort-Object Name | Select-Object -Last 1).FullName
$platform = (Get-ChildItem "$sdk/platforms" -Directory | Sort-Object Name | Select-Object -Last 1).FullName
$dir = "$root/out/player-fixture"
New-Item -ItemType Directory -Force -Path "$dir/classes","$dir/dex" | Out-Null
& "$bt/aapt2.exe" link -o "$dir/test.apk" -I "$platform/android.jar" --manifest "$PSScriptRoot/player/AndroidManifest.xml"
& "$jdk/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -cp "$platform/android.jar" -d "$dir/classes" "$PSScriptRoot/player/Fixture.java"
if ($LASTEXITCODE -ne 0) { throw 'Compile failed' }
$env:JAVA_HOME = $jdk
& "$bt/d8.bat" --min-api 28 --lib "$platform/android.jar" --output "$dir/dex" "$dir/classes/com/google/android/youtube/tv/Fixture.class"
& "$jdk/bin/jar.exe" -u -M -f "$dir/test.apk" -C "$dir/dex" classes.dex
& "$bt/zipalign.exe" -f 4 "$dir/test.apk" "$dir/aligned.apk"
& "$bt/apksigner.bat" sign --ks "$root/freemytv.keystore" --ks-pass pass:freemytv --out "$dir/PlayerFixture.apk" "$dir/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed' }
Write-Output "Built $dir/PlayerFixture.apk (emulator only; never install over real YouTube)"
