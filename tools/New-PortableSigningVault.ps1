[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SigningRoot,
    [string]$PolicyPath = (Join-Path $PSScriptRoot '..\app\release-signing-policy.json'),
    [string]$OutputDirectory = '',
    [string]$ArchiveName = 'FlowBreak-production-signing-vault.7z',
    [string]$SevenZipPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $SigningRoot 'portable-vault-staging'
}

function Resolve-SevenZip {
    param([string]$RequestedPath)

    $candidates = @()
    if ($RequestedPath) { $candidates += $RequestedPath }
    foreach ($commandName in @('7z', '7zz')) {
        $command = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($command) { $candidates += $command.Source }
    }
    $candidates += @(
        'C:\Program Files\7-Zip\7z.exe',
        'C:\Program Files (x86)\7-Zip\7z.exe',
        'C:\Program Files (x86)\Common Files\Adobe\CEP\extensions\com.mtmograph.motion-next\node_modules\win-7zip\7zip-lite\7z.exe'
    )

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw '7-Zip was not found. Install/use an existing 7-Zip binary or pass -SevenZipPath; this script does not install software.'
}

function Assert-File {
    param([string]$Path, [string]$Description)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description not found: $Path"
    }
}

function Get-CertificateFingerprint {
    param([string]$Path)
    Assert-File $Path 'public certificate'
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($Path)
    try {
        return ($certificate.GetCertHashString('SHA256')).ToLowerInvariant()
    } finally {
        $certificate.Dispose()
    }
}

function Invoke-SevenZip {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$Action
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        [void]$startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "7-Zip $Action failed (exit code $($process.ExitCode))."
        }
        return $stdout
    } finally {
        $process.Dispose()
    }
}

function New-RecoverySecret {
    $bytes = [byte[]]::new(32)
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    } finally {
        $random.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Set-RecoverySecretClipboard {
    param([string]$Secret)
    $setClipboard = Get-Command Set-Clipboard -ErrorAction SilentlyContinue
    if (-not $setClipboard) {
        throw 'Set-Clipboard is required for the non-logging recovery-secret handoff. The script will not print or write the secret.'
    }
    Set-Clipboard -Value $Secret
}

function Clear-RecoverySecretClipboard {
    $setClipboard = Get-Command Set-Clipboard -ErrorAction SilentlyContinue
    if ($setClipboard) {
        try { Set-Clipboard -Value '' } catch { }
    }
}

$sevenZip = Resolve-SevenZip $SevenZipPath
Assert-File $PolicyPath 'release-signing-policy.json'

$policy = Get-Content -Raw -LiteralPath $PolicyPath | ConvertFrom-Json
if ($policy.provisioningStatus -ne 'PROVISIONED') {
    throw 'Refusing to create a production vault: release-signing-policy.json is not PROVISIONED with the final human-generated identity.'
}
if ($policy.custody.machineBoundCustody -ne $false) {
    throw 'Refusing to create a production vault: machineBoundCustody must be false.'
}
foreach ($role in @('play', 'domestic')) {
    $fingerprint = [string]$policy.$role.certificateSha256
    if ($fingerprint -notmatch '^[0-9a-f]{64}$') {
        throw "Refusing to create a production vault: final $role certificate fingerprint is missing or invalid."
    }
}

$assets = @(
    [pscustomobject]@{
        Role = 'play'
        Alias = 'flowbreak-play-upload'
        Algorithm = 'RSA 3072'
        Jks = (Join-Path $SigningRoot 'play\flowbreak-play-upload.jks')
        Certificate = (Join-Path $SigningRoot 'public-certificates\flowbreak-play-upload-cert.pem')
    },
    [pscustomobject]@{
        Role = 'domestic'
        Alias = 'flowbreak-domestic-release'
        Algorithm = 'RSA 3072'
        Jks = (Join-Path $SigningRoot 'domestic\flowbreak-domestic-release.jks')
        Certificate = (Join-Path $SigningRoot 'public-certificates\flowbreak-domestic-release-cert.pem')
    }
)
foreach ($asset in $assets) {
    Assert-File $asset.Jks "$($asset.Role) final JKS"
    Assert-File $asset.Certificate "$($asset.Role) public certificate"
    $actualFingerprint = Get-CertificateFingerprint $asset.Certificate
    $expectedFingerprint = ([string]$policy.($asset.Role).certificateSha256).ToLowerInvariant()
    if ($actualFingerprint -ne $expectedFingerprint) {
        throw "$($asset.Role) public certificate does not match the final release-signing policy."
    }
}

$archivePath = Join-Path $OutputDirectory $ArchiveName
$payloadPath = Join-Path $OutputDirectory 'payload'
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
    throw "Refusing to overwrite existing vault: $archivePath"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory, $payloadPath | Out-Null

$recoverySecret = New-RecoverySecret
$manifestPath = Join-Path $payloadPath 'public-fingerprint-metadata.json'
$readmePath = Join-Path $payloadPath 'README.txt'

try {
    $payloadFiles = @(
        @{ Source = $assets[0].Jks; Destination = (Join-Path $payloadPath 'play\flowbreak-play-upload.jks') },
        @{ Source = $assets[0].Certificate; Destination = (Join-Path $payloadPath 'public-certificates\flowbreak-play-upload-cert.pem') },
        @{ Source = $assets[1].Jks; Destination = (Join-Path $payloadPath 'domestic\flowbreak-domestic-release.jks') },
        @{ Source = $assets[1].Certificate; Destination = (Join-Path $payloadPath 'public-certificates\flowbreak-domestic-release-cert.pem') }
    )
    foreach ($file in $payloadFiles) {
        $destinationDirectory = Split-Path -Parent $file.Destination
        New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
        Copy-Item -LiteralPath $file.Source -Destination $file.Destination -Force
    }

    $metadata = [ordered]@{
        schemaVersion = 1
        vaultName = $ArchiveName
        currentPcRole = 'TEMPORARY_SIGNING_WORKSTATION'
        machineBoundCustody = $false
        encryption = [ordered]@{
            format = '7z'
            cipher = 'AES-256'
            encryptFileNames = $true
            recoverySecret = 'NOT_IN_VAULT'
        }
        assets = @(
            [ordered]@{ role = 'play'; alias = $assets[0].Alias; algorithm = $assets[0].Algorithm; certificateSha256 = ([string]$policy.play.certificateSha256).ToLowerInvariant() },
            [ordered]@{ role = 'domestic'; alias = $assets[1].Alias; algorithm = $assets[1].Algorithm; certificateSha256 = ([string]$policy.domestic.certificateSha256).ToLowerInvariant() }
        )
        status = [ordered]@{
            vaultEncryption = 'PENDING_VERIFICATION'
            recoverySecretHandoff = 'PENDING_OWNER_STORAGE'
            offMachineBackup = 'PENDING_OWNER_COPY'
            crossMachineSigningRecovery = 'NOT_YET_TESTED'
        }
    }
    $metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8 -NoNewline

    @"
FLOWBREAK PORTABLE SIGNING VAULT

This vault contains only the final FlowBreak signing identity recovery assets:
- play/flowbreak-play-upload.jks (alias: flowbreak-play-upload; role: Play upload-key)
- domestic/flowbreak-domestic-release.jks (alias: flowbreak-domestic-release; role: Domestic app-signing-key)
- public-certificates/*.pem
- public-fingerprint-metadata.json
- this README

Custody:
- CURRENT_PC_ROLE = TEMPORARY_SIGNING_WORKSTATION
- MACHINE_BOUND_CUSTODY = NO
- Recovery secret is intentionally NOT stored in this vault.
- JKS passwords are intentionally NOT stored in this vault; keep them in the owner's cross-device password manager.

Recovery procedure:
1. Copy this .7z file to the destination machine.
2. Retrieve SIGNING_VAULT_RECOVERY_SECRET from the owner's password manager.
3. Extract with a mature 7-Zip build using the recovery secret. Do not put the secret in shell history or logs.
4. Use keytool to read both JKS public metadata and compare SHA-256 fingerprints with public-fingerprint-metadata.json and the repository release-signing-policy.json.
5. Use the JKS passwords from the owner's password manager only when signing.

This file is not proof of cross-machine recovery. Set CROSS_MACHINE_SIGNING_RECOVERY = PASS only after the restore test on the original laptop or another independent machine passes.
"@ | Set-Content -LiteralPath $readmePath -Encoding UTF8 -NoNewline

    Set-RecoverySecretClipboard $recoverySecret
    Write-Host 'SIGNING_VAULT_RECOVERY_SECRET is available in the system clipboard only.'
    Write-Host 'Paste it into the owner password manager now. The secret is not printed, logged, or written to a file.'
    $savedConfirmation = Read-Host 'Type SAVED after storing the secret in the owner password manager'
    if ($savedConfirmation -cne 'SAVED') {
        throw 'Vault creation aborted because recovery-secret handoff was not confirmed.'
    }

    $archiveArguments = @('a', '-t7z', '-mx=9', '-mhe=on', "-p$recoverySecret", '-y', $archivePath, '*')
    Push-Location $payloadPath
    try {
        [void](Invoke-SevenZip $sevenZip $archiveArguments 'archive creation')
    } finally {
        Pop-Location
    }
    Assert-File $archivePath 'portable vault archive'

    [void](Invoke-SevenZip $sevenZip @('t', "-p$recoverySecret", '-y', $archivePath) 'encryption verification')
    $listing = Invoke-SevenZip $sevenZip @('l', '-slt', "-p$recoverySecret", '-y', $archivePath) 'archive listing verification'
    foreach ($requiredName in @(
        'play\flowbreak-play-upload.jks',
        'domestic\flowbreak-domestic-release.jks',
        'public-certificates\flowbreak-play-upload-cert.pem',
        'public-certificates\flowbreak-domestic-release-cert.pem',
        'public-fingerprint-metadata.json',
        'README.txt'
    )) {
        if ($listing -notmatch [regex]::Escape($requiredName)) {
            throw "Portable vault is missing required asset: $requiredName"
        }
    }
    foreach ($forbiddenName in @('git', 'node_modules', '.gradle', 'source', 'artifact', 'password', 'secret')) {
        if ($listing -match [regex]::Escape($forbiddenName)) {
            throw "Portable vault contains a forbidden path/token: $forbiddenName"
        }
    }

    Write-Host 'VAULT_CREATED=PASS'
    Write-Host 'VAULT_ENCRYPTION=PASS (7z AES-256; encrypted file names)'
    Write-Host 'RECOVERY_SECRET_HANDOFF=PENDING_OWNER_STORAGE'
    Write-Host 'OFF_MACHINE_BACKUP=PENDING_OWNER_COPY'
    Write-Host 'CROSS_MACHINE_SIGNING_RECOVERY=NOT_YET_TESTED'
    Write-Host "VAULT_PATH=$archivePath"
}
finally {
    Clear-RecoverySecretClipboard
    if (Test-Path -LiteralPath $payloadPath) {
        Remove-Item -LiteralPath $payloadPath -Recurse -Force
    }
    $recoverySecret = $null
}
