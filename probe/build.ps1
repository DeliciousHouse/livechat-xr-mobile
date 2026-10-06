# Build the probe APK without Gradle (aapt2 + javac + d8 + apksigner). Needs JDK 17 and Android SDK
# platforms;android-34 + build-tools;34.0.0 under $env:LOCALAPPDATA\android-dev.
$ErrorActionPreference = "Stop"
$dev = "$env:LOCALAPPDATA\android-dev"; $sdk = "$dev\sdk"; $bt = "$sdk\build-tools\34.0.0"; $jar = "$sdk\platforms\android-34\android.jar"
$jdk = (Get-ChildItem $dev -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
$env:JAVA_HOME = $jdk; $env:Path = "$jdk\bin;$env:Path"
Set-Location $PSScriptRoot
$out = Join-Path $PSScriptRoot "build"
if (Test-Path $out) { Get-ChildItem $out -Recurse -File | ForEach-Object { $_.Delete() } }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null
& "$bt\aapt2.exe" compile --dir res -o "$out\res.zip"
& "$bt\aapt2.exe" link -I $jar --manifest AndroidManifest.xml -o "$out\unsigned.apk" "$out\res.zip" --java "$out\gen"
if ($LASTEXITCODE) { throw "aapt2 link failed" }
$src = @(Get-ChildItem -Recurse src, "$out\gen" -Filter *.java | ForEach-Object FullName)
& javac -source 11 -target 11 -encoding UTF-8 -classpath $jar -d "$out\classes" @src
if ($LASTEXITCODE) { throw "javac failed" }
& "$bt\d8.bat" --min-api 29 --lib $jar --output "$out\dex" @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName)
if ($LASTEXITCODE) { throw "d8 failed" }
Push-Location "$out\dex"; & jar uf ..\unsigned.apk classes.dex; Pop-Location
& "$bt\zipalign.exe" -p -f 4 "$out\unsigned.apk" "$out\aligned.apk"
$ks = "$dev\debug.keystore"
if (-not (Test-Path $ks)) { & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LiveChat XR debug" | Out-Null }
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --out "$out\livechatxr-probe.apk" "$out\aligned.apk"
& "$bt\apksigner.bat" verify "$out\livechatxr-probe.apk"
if ($LASTEXITCODE) { throw "apksigner verify failed" }
"built: $out\livechatxr-probe.apk"
