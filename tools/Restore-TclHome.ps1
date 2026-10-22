param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
& $Adb -s $Serial shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
if ($LASTEXITCODE -ne 0) { throw 'Could not restore Google TV Home role.' }
$current = & $Adb -s $Serial shell settings get secure enabled_accessibility_services
if ($LASTEXITCODE -ne 0) { throw 'Could not read accessibility settings.' }
$remaining = @($current.Trim() -split ':' | Where-Object {
    $_ -and $_ -ne 'null' -and $_ -ne 'io.freemytv/io.freemytv.HomeRedirect' -and $_ -ne 'io.freemytv/.HomeRedirect'
})
if ($remaining.Count) {
    & $Adb -s $Serial shell settings put secure enabled_accessibility_services ($remaining -join ':')
} else {
    & $Adb -s $Serial shell settings delete secure enabled_accessibility_services
}
if ($LASTEXITCODE -ne 0) { throw 'Could not remove Home redirect service.' }
& $Adb -s $Serial shell input keyevent KEYCODE_HOME
