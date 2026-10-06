# Install the probe on a USB-connected Quest (developer mode, USB debugging allowed), enable it, show a banner.
# Usage: .\run-on-quest.ps1 [-Remove]
param([switch]$Remove)
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:APPDATA\SideQuest\platform-tools\adb.exe" }
$svc = "com.livechatxr.probe/com.livechatxr.probe.BannerService"

if ($Remove) {
    & $adb shell settings delete secure enabled_accessibility_services
    & $adb uninstall com.livechatxr.probe
    return
}
& $adb install -r "$PSScriptRoot\build\livechatxr-probe.apk"
& $adb shell appops set com.livechatxr.probe SYSTEM_ALERT_WINDOW allow   # for the "app" overlay comparison
& $adb shell settings put secure enabled_accessibility_services $svc
& $adb shell settings put secure accessibility_enabled 1
Start-Sleep 3
"enabled services: " + (& $adb shell settings get secure enabled_accessibility_services)
& $adb shell am broadcast -a com.livechatxr.probe.SHOW --es text "LiveChat XR probe: can you see me?" | Out-Null
Start-Sleep 2
& $adb logcat -d -s LCXRProbe:* | Select-Object -Last 10
