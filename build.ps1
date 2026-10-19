# Builds out\tvhome.apk on Windows. Same steps as build.sh.
# Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0): set JAVA_HOME and ANDROID_HOME,
# or keep them where this script looks by default.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME)   { $env:JAVA_HOME   = "$env:USERPROFILE\tools\jdk17" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:USERPROFILE\tools\android-sdk" }
$bt  = "$env:ANDROID_HOME\build-tools\34.0.0"
$jar = "$env:ANDROID_HOME\platforms\android-34\android.jar"
$bin = "$env:JAVA_HOME\bin"
if (-not (Test-Path $jar) -or -not (Test-Path $bt)) { throw "Android SDK not found. Install platforms;android-34 and build-tools;34.0.0." }

function Run($exe, $argList) {
    & $exe @argList
    if ($LASTEXITCODE -ne 0) { throw "$exe failed ($LASTEXITCODE)" }
}

Remove-Item out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force out\classes, out\dex, out\gen\com\tvhome\example | Out-Null

# Turn the app list into Apps.java.
$lines = Get-Content $(if (Test-Path apps.local.txt) { "apps.local.txt" } else { "apps.txt" }) | Where-Object { $_ -notmatch '^\s*#' } | ForEach-Object { $_.Trim() } | Where-Object { $_ }
$body = ($lines | ForEach-Object { "    `"$_`"," }) -join "`n"
"package com.tvhome.example;`nfinal class Apps { static final String[] LIST = {`n$body`n}; }" |
    Set-Content out\gen\com\tvhome\example\Apps.java -Encoding ascii

Run "$bt\aapt2.exe" @("link", "-o", "out\base.apk", "-I", $jar, "--manifest", "AndroidManifest.xml", "-A", "assets", "--min-sdk-version", "21", "--target-sdk-version", "34")

$sources = @(Get-ChildItem src -Recurse -Filter *.java) + @(Get-ChildItem out\gen -Recurse -Filter *.java) | ForEach-Object { $_.FullName }
Run "$bin\javac.exe" (@("-encoding", "UTF-8", "-nowarn", "-Xlint:-options", "-source", "1.8", "-target", "1.8", "-bootclasspath", "$jar;$bt\core-lambda-stubs.jar", "-d", "out\classes") + $sources)
Run "$bin\jar.exe" @("cf", "out\classes.jar", "-C", "out\classes", ".")
Run "$bin\java.exe" @("-cp", "$bt\lib\d8.jar", "com.android.tools.r8.D8", "--min-api", "21", "--release", "--lib", $jar, "--output", "out\dex", "out\classes.jar")

Copy-Item out\base.apk out\unsigned.apk
Run "$bin\jar.exe" @("uf", "out\unsigned.apk", "-C", "out\dex", "classes.dex")
Run "$bt\zipalign.exe" @("-f", "4", "out\unsigned.apk", "out\aligned.apk")

# A signing key is made on first build, outside the repo, in ~\.tvhome (key.jks + key.pass). Keep both:
# updates must be signed with the same key. To use another key, set KEYSTORE and KEYSTORE_PASS.
$kdir = "$env:USERPROFILE\.tvhome"
New-Item -ItemType Directory -Force $kdir | Out-Null
$ks = if ($env:KEYSTORE) { $env:KEYSTORE } else { "$kdir\key.jks" }
if ($env:KEYSTORE_PASS) { $pass = $env:KEYSTORE_PASS }
else {
    if (-not (Test-Path "$kdir\key.pass")) {
        $chars = [char[]]([char]'A'..[char]'Z' + [char]'a'..[char]'z' + [char]'0'..[char]'9')
        (-join (1..24 | ForEach-Object { $chars | Get-Random })) | Set-Content "$kdir\key.pass" -NoNewline -Encoding ascii
    }
    $pass = (Get-Content "$kdir\key.pass" -Raw).Trim()
}
if (-not (Test-Path $ks)) {
    Run "$bin\keytool.exe" @("-genkeypair", "-keystore", $ks, "-storepass", $pass, "-keypass", $pass, "-alias", "tvhome",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=tvhome")
}
Run "$bt\apksigner.bat" @("sign", "--ks", $ks, "--ks-pass", "pass:$pass", "--out", "out\tvhome.apk", "out\aligned.apk")
"Built out\tvhome.apk ($([math]::Round((Get-Item out\tvhome.apk).Length / 1KB)) KB)"

