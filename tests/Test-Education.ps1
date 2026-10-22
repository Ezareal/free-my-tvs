$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
$classes = Join-Path $root 'out/education-tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& "$jdk/bin/javac.exe" -encoding UTF-8 -d $classes "$root/app/src/io/freemytv/EducationRules.java" "$PSScriptRoot/EducationRulesTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$jdk/bin/java.exe" -cp $classes io.freemytv.EducationRulesTest
if ($LASTEXITCODE -ne 0) { throw 'Education tests failed' }
