# SPDX-License-Identifier: GPL-3.0-only
# Windows PowerShell 5.1+. Always resolves files relative to this script.
[CmdletBinding()]
param([string]$AdbPath, [string]$Serial, [switch]$NoLaunch, [switch]$NoDownload)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$deviceArgs = @()

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','') }
    finally { $stream.Dispose(); $sha.Dispose() }
}

function Invoke-Adb([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $script:adb @script:deviceArgs @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    if ($code -ne 0 -or $text -match '(?m)^Failure \[|SecurityException|java\.lang\..*Exception') {
        throw "ADB failed / ADB fehlgeschlagen: $($Arguments -join ' ')`n$text"
    }
    return $text
}

try {
    Write-Host 'PFD Metrics - Setup / Repair / Einrichtung (Windows)' -ForegroundColor Cyan
    Write-Host 'Connect the headset via USB and allow USB debugging in the headset. / Headset per USB verbinden, aufsetzen und USB-Debugging erlauben.'
    # Check the release before installing either package.
    $manifestPath = Join-Path $root 'release.json'
    if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Missing release.json. Extract the entire release ZIP, not just setup.cmd. / release.json fehlt. Bitte das vollstaendige Release-ZIP entpacken.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    foreach ($name in @('PFDMetricsHUD.apk','PFDMetricsTool.apk')) {
        $file = Join-Path $root $name
        if (!(Test-Path -LiteralPath $file)) { throw "Missing $name. Extract the entire release ZIP. / $name fehlt. Bitte das gesamte Release-ZIP entpacken." }
        $expected = $manifest.sha256.$name
        if (!$expected -or (Get-Sha256 $file) -ne $expected) {
            throw "Checksum mismatch: $name. Download and extract the release again. / Pruefsumme von $name stimmt nicht. Release erneut herunterladen/entpacken."
        }
    }
    if ($AdbPath) {
        $script:adb = (Resolve-Path -LiteralPath $AdbPath).Path
    } else {
        $localAdb = Join-Path $root 'tools\platform-tools\adb.exe'
        $found = Get-Command adb.exe -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $localAdb) { $script:adb = $localAdb }
        elseif ($found) { $script:adb = $found.Source }
        else {
            if ($NoDownload) { throw 'ADB missing. Extract Platform-Tools to tools\platform-tools or specify -AdbPath. / ADB fehlt. Platform-Tools nach tools\platform-tools entpacken oder -AdbPath angeben.' }
            Write-Host 'Downloading Android Platform-Tools from Google (once; internet required). / Lade Android Platform-Tools direkt von Google (einmalig, Internet erforderlich).'
            Write-Host 'Source and license terms / Quelle und Lizenzbedingungen: https://developer.android.com/tools/releases/platform-tools'
            $toolsDir = Join-Path $root 'tools'
            New-Item -ItemType Directory -Force $toolsDir | Out-Null
            $zip = Join-Path $toolsDir ('download-' + [Guid]::NewGuid().ToString('N') + '.zip')
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            try {
                Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
                Expand-Archive -LiteralPath $zip -DestinationPath $toolsDir -Force
            } finally {
                if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip }
            }
            if (!(Test-Path -LiteralPath $localAdb)) { throw 'Google Platform-Tools contain no adb.exe. Check the download. / Google Platform-Tools enthalten keine adb.exe. Bitte den Download pruefen.' }
            $script:adb = $localAdb
        }
    }
    Write-Host "ADB: $script:adb"
    # ADB daemon startup may write normal informational messages to stderr.
    Invoke-Adb -Arguments @('start-server') | Write-Host
    $listing = Invoke-Adb -Arguments @('devices','-l')
    $devices = @($listing -split "`r?`n" | Where-Object { $_ -match '^\S+\s+(device|offline|unauthorized)(\s|$)' } | ForEach-Object {
        $parts = $_ -split '\s+'
        [pscustomobject]@{ Serial=$parts[0]; State=$parts[1] }
    })
    if ($Serial) { $selected = @($devices | Where-Object Serial -eq $Serial) }
    else {
        if ($devices.Count -gt 1) { throw "Multiple devices connected. Disconnect others or use setup.cmd -Serial DEVICE. / Mehrere Geraete verbunden. Andere trennen oder setup.cmd -Serial GERAET verwenden.`n$listing" }
        $selected = $devices
    }
    if ($selected.Count -ne 1) { throw 'No matching headset found. Check the USB data cable, developer mode and Windows USB driver; run setup.cmd again. / Kein passendes Headset gefunden. USB-Datenkabel, Entwicklermodus und Windows-USB-Treiber pruefen; setup.cmd erneut starten.' }
    if ($selected[0].State -eq 'unauthorized') { throw 'Allow this computer for USB debugging in the headset, then run setup.cmd again. / USB-Debugging im Headset bestaetigen (Computer zulassen), dann setup.cmd erneut starten.' }
    if ($selected[0].State -ne 'device') { throw 'Headset offline. Reconnect USB, wake the headset and retry. / Headset ist offline. USB neu verbinden, Headset aufwecken und erneut starten.' }
    $script:deviceArgs = @('-s', $selected[0].Serial)
    $model = (Invoke-Adb -Arguments @('shell','getprop','ro.product.model')).Trim()
    $sdk = (Invoke-Adb -Arguments @('shell','getprop','ro.build.version.sdk')).Trim()
    if ($model -notmatch '^PFDM[ _]MR$' -or $sdk -ne '34') { throw "This release requires PFDM MR with Android 14 (SDK 34). Detected: $model / SDK $sdk. Nothing installed. / Dieses Release ist fuer PFDM MR mit Android 14 (SDK 34) getestet. Erkannt: $model / SDK $sdk. Keine Installation ausgefuehrt." }
    Write-Host "Headset: $model / Android SDK $sdk"
    foreach ($name in @('PFDMetricsHUD.apk','PFDMetricsTool.apk')) {
        Write-Host "Installing / Installiere $name ..."
        try {
            $result = Invoke-Adb -Arguments @('install','--no-incremental','-r',(Join-Path $root $name))
            if ($result -notmatch '(?m)^Success\s*$') { throw $result }
        } catch {
            throw "Installation failed: $name. INSTALL_FAILED_UPDATE_INCOMPATIBLE means the existing APK uses a different signing key. No automatic uninstall; settings are preserved. / Installation von $name fehlgeschlagen. Bei INSTALL_FAILED_UPDATE_INCOMPATIBLE stammen vorhandene APKs von einem anderen Signierschluessel. Nicht automatisch deinstalliert; Einstellungen bleiben erhalten.`n$_"
        }
    }
    $hud = 'com.pfd.metricstool.hud'
    # An update receiver can turn monitoring off; run setup before starting the UI.
    foreach ($permission in @('READ_LOGS','DUMP','POST_NOTIFICATIONS')) {
        Write-Host "Permission / Berechtigung: $permission"
        Invoke-Adb -Arguments @('shell','pm','grant',$hud,"android.permission.$permission") | Out-Null
    }
    foreach ($operation in @('SYSTEM_ALERT_WINDOW','GET_USAGE_STATS','MANAGE_EXTERNAL_STORAGE','WRITE_SETTINGS')) {
        Invoke-Adb -Arguments @('shell','appops','set',$hud,$operation,'allow') | Out-Null
    }
    Write-Host 'Checking installation and permissions / Pruefe Installation und Rechte ...'
    foreach ($pkg in @($hud,'com.pfd.metricstool')) {
        $dump = Invoke-Adb -Arguments @('shell','dumpsys','package',$pkg)
        if ($dump -notmatch ('versionName=' + [regex]::Escape($manifest.version) + '(\s|$)')) { throw "Unexpected version / Unerwartete Version fuer $pkg" }
        if ($pkg -eq $hud) {
            foreach ($permission in @('READ_LOGS','DUMP','POST_NOTIFICATIONS')) {
                if ($dump -notmatch "android\.permission\.${permission}: granted=true") { throw "Permission $permission was not granted. / Berechtigung $permission wurde nicht erteilt." }
            }
        }
    }
    foreach ($operation in @('SYSTEM_ALERT_WINDOW','GET_USAGE_STATS','MANAGE_EXTERNAL_STORAGE','WRITE_SETTINGS')) {
        $result = Invoke-Adb -Arguments @('shell','appops','get',$hud,$operation)
        if ($result -notmatch ($operation + ': allow')) { throw "AppOp $operation not allowed / nicht erlaubt: $result" }
    }
    if (!$NoLaunch) { Invoke-Adb -Arguments @('shell','am','start','-n','com.pfd.metricstool/.MainActivity') | Write-Host }
    Write-Host 'DONE / FERTIG: Both apps and all required permissions are ready. / Beide Apps und alle benoetigten Rechte sind eingerichtet.' -ForegroundColor Green
    Write-Host 'Enable monitoring in the headset and allow access to device logs. / Im HMD Monitoring einschalten und einmal den Zugriff auf Geraeteprotokolle erlauben.'
    Write-Host 'Disconnect USB. After a headset reboot, approve log access again in the headset. / Danach USB abziehen. Nach einem HMD-Neustart nur im HMD erneut bestaetigen.'
    Write-Host 'No log helper or PC script is needed after rebooting. / Kein Log-Helper und kein PC-Skript sind nach Neustarts erforderlich.'
    exit 0
} catch {
    Write-Host "ERROR / FEHLER: $_" -ForegroundColor Red
    Write-Host 'Setup did not complete successfully. Fix the error and run setup again. / Die Einrichtung wurde nicht als erfolgreich abgeschlossen. Nach Behebung erneut starten.'
    exit 1
}

