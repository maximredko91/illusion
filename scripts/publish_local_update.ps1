# Copies the local-network update mirror (manifest.json + per-ABI APKs) to the NAS.
# Run this by hand after each GitHub release - it is NOT run automatically by Claude.
#
# Usage (interactive prompt for username/password each run):
#   .\scripts\publish_local_update.ps1 -NasHost 192.168.2.1 -Share Movies -SourceDir "C:\path\to\the\three\files"
#
# Usage (username/password passed directly, no prompt):
#   .\scripts\publish_local_update.ps1 -NasHost 192.168.2.1 -Share Movies -SourceDir "..." -Username admin -Password "your-password"
#
# -Password is plain text on the command line if you pass it directly - it lands in this
# console's command history (and PSReadLine's saved history file) for as long as that lasts.
# Skip -Password (and -Username) to get the interactive console prompt instead, which never
# touches history at all - safer if that matters to you. Neither is ever written to disk by
# this script itself either way.

param(
    [Parameter(Mandatory=$true)][string]$NasHost,
    [Parameter(Mandatory=$true)][string]$Share,
    [Parameter(Mandatory=$true)][string]$SourceDir,
    [string]$RemoteFolder = "IllusionUpdates",
    [string]$Username,
    [string]$Password
)

if ($Username -and $Password) {
    $smbUser = $Username
    $smbPassSecure = ConvertTo-SecureString $Password -AsPlainText -Force
} else {
    # Console-based prompt (not Get-Credential's GUI popup) - the popup can open off-screen or
    # behind other windows depending on the terminal/session, which showed as the script silently
    # hanging with no visible prompt at all.
    $smbUser = Read-Host "SMB username for \\$NasHost\$Share"
    $smbPassSecure = Read-Host "SMB password" -AsSecureString
}
$cred = New-Object System.Management.Automation.PSCredential($smbUser, $smbPassSecure)
$drive = "Z_IllusionUpdates"

try {
    New-PSDrive -Name $drive -PSProvider FileSystem -Root "\\$NasHost\$Share" -Credential $cred -ErrorAction Stop | Out-Null

    $destPath = Join-Path "${drive}:" $RemoteFolder
    if (-not (Test-Path $destPath)) {
        New-Item -ItemType Directory -Path $destPath | Out-Null
    }

    # Copy-Item only overwrites files that share a name with something in SourceDir - it never
    # deletes anything. APK filenames carry the version number (illusion-98-arm64-v8a.apk,
    # illusion-99-arm64-v8a.apk, ...), so every release's APKs are new filenames, not overwrites -
    # without this cleanup step, every past release's APKs would just pile up on the NAS forever.
    # Deletes any .apk already on the NAS that isn't also present in SourceDir, before copying the
    # new ones in - manifest.json itself is always just overwritten in place (same name every time).
    $newApkNames = (Get-ChildItem -Path $SourceDir -Filter "*.apk").Name
    $existingApks = Get-ChildItem -Path $destPath -Filter "*.apk" -ErrorAction SilentlyContinue
    foreach ($old in $existingApks) {
        if ($newApkNames -notcontains $old.Name) {
            Remove-Item -Path $old.FullName -Force
            Write-Host "Removed old: $($old.Name)"
        }
    }

    Copy-Item -Path (Join-Path $SourceDir "manifest.json") -Destination $destPath -Force
    Copy-Item -Path (Join-Path $SourceDir "*.apk") -Destination $destPath -Force

    Write-Host "Done: manifest.json + APK files copied to \\$NasHost\$Share\$RemoteFolder"

    # Verification pass - re-reads manifest.json back FROM the NAS (not from SourceDir) and
    # checks every listed asset actually exists there with the exact byte size the manifest
    # claims. Catches the two failure modes that would otherwise only surface later, silently,
    # inside the app itself: a copy that didn't fully complete (truncated file), or a manifest
    # referencing a filename that doesn't match what's actually sitting on the share (exactly
    # what happened here when APK filenames lost their hyphens partway through download).
    Write-Host ""
    Write-Host "Verifying..."
    $manifestOnNas = Get-Content -Path (Join-Path $destPath "manifest.json") -Raw | ConvertFrom-Json
    $allOk = $true
    foreach ($asset in $manifestOnNas.assets) {
        $assetPath = Join-Path $destPath $asset.fileName
        if (-not (Test-Path $assetPath)) {
            Write-Host "  MISSING: $($asset.fileName) (manifest expects it, not found on NAS)" -ForegroundColor Red
            $allOk = $false
            continue
        }
        $actualSize = (Get-Item $assetPath).Length
        if ($actualSize -ne $asset.sizeBytes) {
            Write-Host "  SIZE MISMATCH: $($asset.fileName) - manifest says $($asset.sizeBytes) bytes, actual is $actualSize bytes" -ForegroundColor Red
            $allOk = $false
        } else {
            Write-Host "  OK: $($asset.fileName) ($actualSize bytes)"
        }
    }
    if ($allOk) {
        Write-Host "Verification passed - versionCode $($manifestOnNas.versionCode) ($($manifestOnNas.versionName)) is ready." -ForegroundColor Green
    } else {
        Write-Host "Verification FAILED - fix the issue(s) above before testing on-device." -ForegroundColor Red
    }
} finally {
    Remove-PSDrive -Name $drive -ErrorAction SilentlyContinue
}
