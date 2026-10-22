# Windows version of build-and-install.sh: build the launcher APK and install it on the TV as the
# default home screen. Uses Android Studio's bundled JDK and the default SDK location unless
# JAVA_HOME / ANDROID_HOME are set.
#
# Usage: launcher\build-and-install.ps1 -TvHost 192.168.1.50 [-StockLauncher <package>|none]
#        The stock launcher is detected from the TV when -StockLauncher is not given.
#        [-InputKeyService]   remote's Input button opens the input switcher (Google TV), plus config.xml
#                             input_key_keycodes and app_keys; re-pass on every install
#        [-HomeKeyService]    remotes whose Home button sends KEYCODE_ALL_APPS (Xiaomi sticks)
#        [-MusicDir <folder>] your own menu / winter / shop / games music (.mp3, .ogg, .m4a, .flac, .wav), copied to the TV
#        [-NoSmartTube]       don't add SmartTube (YouTube without ads, shown as the YouTube channel)
#        Fire TV is detected automatically (see build-and-install.sh): the Amazon launcher is left alone and
#        the FireTvHomeService accessibility service takes the Home button instead.
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$StockLauncher = '',
    [switch]$InputKeyService,
    [switch]$HomeKeyService,
    # A ready-made APK (e.g. the one CI attaches to each run): skips the build, no JDK or SDK needed.
    [string]$ApkFile = '',
    # Your own music: menu.*, winter.*, shop.* and games.* in this folder play instead of the built-in tunes.
    [string]$MusicDir = '',
    [switch]$NoSmartTube
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if ($ApkFile) { $ApkFile = (Resolve-Path $ApkFile).Path }
if ($MusicDir) { $MusicDir = (Resolve-Path $MusicDir).Path }
Set-Location $PSScriptRoot

if (-not $ApkFile) {
    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
        if (-not $jbr) { throw 'Set JAVA_HOME to a JDK 17+ (Android Studio ships one in its jbr folder)' }
        $env:JAVA_HOME = $jbr
    }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
    if (-not (Test-Path local.properties)) {
        "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties
    }

    .\gradlew.bat assembleRelease -q --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
}

$py = '..\tools\venv\Scripts\python.exe'
$apk = if ($ApkFile) { $ApkFile } else { 'app\build\outputs\apk\release\app-release.apk' }
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

# SmartTube: the launcher downloads it from its GitHub page on the TV, and Android asks to confirm there.
function Get-SmartTube {
    if ($NoSmartTube) { return }
    $have = "$(& $py -I ..\tools\tv.py 'pm path org.smarttube.stable; pm path com.liskovsoft.smarttubetv' 2>$null)"
    if ($have -match 'package:') { Write-Host 'SmartTube is already installed.'; return }
    & $py -I ..\tools\tv.py 'am start -n com.gathin.tvhome/.WiiMenuActivity --es get org.smarttube.stable' | Out-Null
    Write-Host 'SmartTube is downloading on the TV: when it asks, choose Install (it shows up as your YouTube channel).'
}

function Push-Music {
    if (-not $MusicDir) { return }
    $dest = '/sdcard/Android/data/com.gathin.tvhome/files/music'
    & $py -I ..\tools\tv.py "mkdir -p $dest" | Out-Null
    $files = Get-ChildItem $MusicDir -File | Where-Object {
        $_.BaseName -in 'menu', 'winter', 'shop', 'games' -and $_.Extension -in '.mp3', '.ogg', '.m4a', '.flac', '.wav' }
    if (-not $files) { Write-Warning "no menu, winter, shop or games music files in $MusicDir"; return }
    foreach ($f in $files) {
        & $py -I ..\tools\tv.py "rm -f $dest/$($f.BaseName).*" | Out-Null
        & $py -I ..\tools\b64push.py $f.FullName "$dest/$($f.Name)"
    }
}

# Fire TV: Fire OS protects its launcher, so leave it alone and redirect the Home button instead.
$maker = "$(& $py -I ..\tools\tv.py 'getprop ro.product.manufacturer' | Select-Object -Last 1)".Trim()
if ($maker -like 'Amazon*') {
    Write-Host 'Fire TV detected: installing with the Home button redirect (the Amazon launcher is left alone).'
    & $py -I ..\tools\b64push.py $apk /data/local/tmp/tvhome.apk
    if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
    $out = & $py -I ..\tools\tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk'
    Write-Host $out
    if ("$out" -notmatch 'Success') { throw 'pm install failed' }
    Push-Music
    $start = (& $py -I ..\tools\tv.py 'am start -W -n com.gathin.tvhome/.WiiMenuActivity') -join "`n"
    Write-Host $start
    if ($start -notmatch 'Status: ok' -or $start -match '(?m)^Error') { throw 'the launcher did not start (see above); the Home button was not redirected' }
    $svc = 'com.gathin.tvhome/.FireTvHomeService'
    @(
        'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted',
        'pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; echo secure-settings-granted',
        'appops set com.gathin.tvhome WRITE_SETTINGS allow 2>/dev/null; echo write-settings-allowed',
        'cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener 2>/dev/null; echo media-listener-allowed',
        'appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow 2>/dev/null; echo updates-allowed',
        'cmd package compile -m speed -f com.gathin.tvhome',
        "s=`$(settings get secure enabled_accessibility_services); case `"`$s`" in *FireTvHomeService*) ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services `"`$s:$svc`" ;; esac; settings put secure accessibility_enabled 1; echo FireTvHomeService enabled",
        'am start -n com.gathin.tvhome/.WiiMenuActivity'
    ) | & $py -I ..\tools\tv.py --batch
    Get-SmartTube
    Write-Host 'Installed. Press Home on the remote to see it; hold Home for Settings.'
    exit 0
}

# Which app owns Home differs by build; ask the TV, but only accept a launcher we recognise (the answer
# can also be Android's chooser "android", the setup wizard, or another third-party launcher).
$knownStock = @('com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.mitv.tvhome')
if (-not $StockLauncher) {
    $homePkg = & $py -I ..\tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1
    if ($LASTEXITCODE -ne 0 -or -not $homePkg) { throw 'could not ask the TV which launcher owns Home; check -TvHost and the debugging prompt, or pass -StockLauncher <package>' }
    $homePkg = "$homePkg".Trim().Split('/')[0]
    if ($knownStock -contains $homePkg) { $StockLauncher = $homePkg }
    elseif ($homePkg -eq 'com.gathin.tvhome') { $StockLauncher = 'none'; Write-Host 'Home already belongs to this launcher; nothing will be disabled.' }
    elseif ($homePkg -eq 'android') {
        # Android's chooser: launchers tie and no default is set (a restore followed by a re-applied list
        # ends here). Take the one known stock launcher among the HOME claimants, if there is exactly one.
        $claimants = (& $py -I ..\tools\tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME') -join "`n"
        $found = @($knownStock | Where-Object { $claimants -match ([regex]::Escape($_) + '/') })
        if ($found.Count -eq 1) { $StockLauncher = $found[0]; Write-Host "Home is Android's chooser; the stock launcher among the candidates is $StockLauncher." }
        else { throw "Home is Android's launcher chooser and $($found.Count) known stock launchers claim Home: pick a default on the TV first, or run again with -StockLauncher <package to disable> or -StockLauncher none." }
    }
    else { throw "Home currently resolves to '$homePkg', which is not a stock launcher this script knows (android = launcher chooser, com.google.android.tungsten.setupwraith = setup wizard, see README Troubleshooting; anything else = another launcher is the default). Run again with -StockLauncher <package to disable> or -StockLauncher none." }
}
Write-Host "Stock launcher: $StockLauncher"
# Google TV: with launcherx off, the remote's Input and YouTube buttons do nothing; InputKeyService answers them.
if ($StockLauncher -eq 'com.google.android.apps.tv.launcherx' -and -not $PSBoundParameters.ContainsKey('InputKeyService')) {
    $InputKeyService = $true
    Write-Host "Google TV: the remote's Input and YouTube buttons will be handled by InputKeyService."
}
$disableStock = if ($StockLauncher -eq 'none') { 'echo stock launcher left alone' } else { "pm disable-user --user 0 $StockLauncher" }

& $py -I ..\tools\b64push.py $apk /data/local/tmp/tvhome.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I ..\tools\tv.py 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk'
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed; the stock launcher was left untouched' }
Push-Music
# Android 9 only accepts a component here; Android 10+ also takes a bare package name.
$out = & $py -I ..\tools\tv.py 'cmd package set-home-activity --user 0 com.gathin.tvhome/.WiiMenuActivity'
Write-Host $out
if ("$out" -match 'Error') { throw 'could not set the new launcher as home; the stock launcher was left untouched' }
# Start the launcher on its own, before the stock Home is touched (see build-and-install.sh, #239):
# in the batch a failed start only prints an Error line and the stock launcher would be disabled anyway.
$start = (& $py -I ..\tools\tv.py 'am start -W -n com.gathin.tvhome/.WiiMenuActivity') -join "`n"
Write-Host $start
if ($start -notmatch 'Status: ok' -or $start -match '(?m)^Error') { throw "the new launcher did not start (see above); $StockLauncher was left enabled" }
@(
    'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted',
    'pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; echo secure-settings-granted',
    'appops set com.gathin.tvhome WRITE_SETTINGS allow 2>/dev/null; echo write-settings-allowed',
    'cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener 2>/dev/null; echo media-listener-allowed',
    'appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow 2>/dev/null; echo updates-allowed',
    'cmd package compile -m speed -f com.gathin.tvhome',
    $disableStock,
    'cmd package set-home-activity --user 0 com.gathin.tvhome/.WiiMenuActivity',
    'am force-stop com.gathin.tvhome',
    'am start -a android.intent.action.MAIN -c android.intent.category.HOME'
) | & $py -I ..\tools\tv.py --batch
# Order matters, see build-and-install.sh: start the new launcher, disable the stock one, set the
# preference last, then check where Home ended up and put the stock launcher back if it is not ours.
$homeNow = (& $py -I ..\tools\tv.py 'cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN' | Select-Object -Last 1).Trim()
if ($homeNow -notlike 'com.gathin.tvhome/*') {
    if ($StockLauncher -ne 'none') {
        & $py -I ..\tools\tv.py "pm enable --user 0 $StockLauncher" | Out-Null
        # set-home-activity needs pkg/activity on Android 9, so resolve the stock component on the TV.
        & $py -I ..\tools\tv.py "cmd package set-home-activity --user 0 `$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $StockLauncher | tail -n 1)" | Out-Null
    }
    throw "Home resolves to '$homeNow', not to the new launcher; $StockLauncher was re-enabled. On Google TV this usually means com.google.android.tungsten.setupwraith also claims Home: add it to your debloat list (see README, Troubleshooting), apply it, then run this script again."
}
Write-Host "Home now resolves to $homeNow"
# Optional accessibility services, appended to enabled_accessibility_services without dropping others.
function Enable-Service([string]$Name) {
    $svc = "com.gathin.tvhome/.$Name"
    # Through stdin: Windows PowerShell 5.1 drops the inner double quotes from a native argument, and the
    # TV's shell then fails with "no closing quote" while the service stays off.
    ('s=$(settings get secure enabled_accessibility_services); case "$s" in *' + $Name + '*) ;; null|"") settings put secure enabled_accessibility_services ' + $svc + ' ;; *) settings put secure enabled_accessibility_services "$s:' + $svc + '" ;; esac; settings put secure accessibility_enabled 1; echo ' + $Name + ' enabled') |
        & $py -I ..\tools\tv.py --batch
}
if ($InputKeyService) { Enable-Service 'InputKeyService' }
if ($HomeKeyService) { Enable-Service 'HomeKeyService' }
Get-SmartTube
Write-Host 'Installed. Press Home on the remote to see it.'
