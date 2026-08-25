[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$VaultPath,
    [Parameter(Mandatory = $true)]
    [string]$PolicyPath,
    [string]$ExtractionDirectory = (Join-Path $env:TEMP ('flowbreak-vault-restore-' + [guid]::NewGuid().ToString('N'))),
    [string]$SevenZipPath = '',
    [string]$KeytoolPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-Tool {
    param([string]$RequestedPath, [string[]]$Names, [string[]]$Candidates, [string]$Description)
    $paths = @()
    if ($RequestedPath) { $paths += $RequestedPath }
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { $paths += $command.Source }
    }
    $paths += $Candidates
    foreach ($path in ($paths | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $path -PathType Leaf) { return (Resolve-Path -LiteralPath $path).Path }
    }
    throw "$Description was not found. Pass its path explicitly."
}

function Read-SecretText {
    param([string]$Prompt)
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Invoke-Process {
    param([string]$Executable, [string[]]$Arguments, [string]$Action)
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) { [void]$startInfo.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "$Action failed (exit code $($process.ExitCode))." }
        return $stdout
    } finally { $process.Dispose() }
}

function Get-CertificateFingerprint {
    param([string]$Path)
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($Path)
    try { return $certificate.GetCertHashString('SHA256').ToLowerInvariant() }
    finally { $certificate.Dispose() }
}

if (-not (Test-Path -LiteralPath $VaultPath -PathType Leaf)) { throw "Vault not found: $VaultPath" }
if (-not (Test-Path -LiteralPath $PolicyPath -PathType Leaf)) { throw "Policy not found: $PolicyPath" }
$sevenZip = Resolve-Tool $SevenZipPath @('7z', '7zz') @(
    'C:\Program Files\7-Zip\7z.exe',
    'C:\Program Files (x86)\7-Zip\7z.exe',
    'C:\Program Files (x86)\Common Files\Adobe\CEP\extensions\com.mtmograph.motion-next\node_modules\win-7zip\7zip-lite\7z.exe'
) '7-Zip'
$keytool = Resolve-Tool $KeytoolPath @('keytool') @(
    'D:\environment\java\jdk-21.0.12+8\bin\keytool.exe',
    'D:\environment\java\jdk-17.0.20+8\bin\keytool.exe'
) 'keytool'

$policy = Get-Content -Raw -LiteralPath $PolicyPath | ConvertFrom-Json
if ($policy.provisioningStatus -ne 'PROVISIONED') { throw 'The supplied policy is not PROVISIONED with a final identity.' }
$recoverySecret = Read-SecretText 'SIGNING_VAULT_RECOVERY_SECRET (input is not displayed)'
$playPassword = Read-SecretText 'Play JKS password (input is not displayed)'
$domesticPassword = Read-SecretText 'Domestic JKS password (input is not displayed)'

New-Item -ItemType Directory -Force -Path $ExtractionDirectory | Out-Null
try {
    [void](Invoke-Process $sevenZip @('t', "-p$recoverySecret", '-y', $VaultPath) 'vault decryption verification')
    [void](Invoke-Process $sevenZip @('x', "-p$recoverySecret", '-y', "-o$ExtractionDirectory", $VaultPath) 'vault extraction')

    $playJks = Join-Path $ExtractionDirectory 'play\flowbreak-play-upload.jks'
    $domesticJks = Join-Path $ExtractionDirectory 'domestic\flowbreak-domestic-release.jks'
    $playCertificate = Join-Path $ExtractionDirectory 'public-certificates\flowbreak-play-upload-cert.pem'
    $domesticCertificate = Join-Path $ExtractionDirectory 'public-certificates\flowbreak-domestic-release-cert.pem'
    foreach ($path in @($playJks, $domesticJks, $playCertificate, $domesticCertificate)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Restored vault asset missing: $path" }
    }

    $playMetadata = Invoke-Process $keytool @('-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v', '-keystore', $playJks, '-storepass', $playPassword) 'Play keytool metadata read'
    $domesticMetadata = Invoke-Process $keytool @('-J-Duser.language=en', '-J-Duser.country=US', '-list', '-v', '-keystore', $domesticJks, '-storepass', $domesticPassword) 'Domestic keytool metadata read'
    if ($playMetadata -notmatch 'flowbreak-play-upload') { throw 'Play JKS alias metadata mismatch.' }
    if ($domesticMetadata -notmatch 'flowbreak-domestic-release') { throw 'Domestic JKS alias metadata mismatch.' }
    if ($playMetadata -notmatch 'SHA256') { throw 'Play JKS public fingerprint metadata was not readable.' }
    if ($domesticMetadata -notmatch 'SHA256') { throw 'Domestic JKS public fingerprint metadata was not readable.' }

    $playFingerprint = Get-CertificateFingerprint $playCertificate
    $domesticFingerprint = Get-CertificateFingerprint $domesticCertificate
    if ($playFingerprint -ne ([string]$policy.play.certificateSha256).ToLowerInvariant()) { throw 'Play fingerprint does not match release-signing-policy.json.' }
    if ($domesticFingerprint -ne ([string]$policy.domestic.certificateSha256).ToLowerInvariant()) { throw 'Domestic fingerprint does not match release-signing-policy.json.' }

    Write-Host 'VAULT_RESTORE_TEST=PASS'
    Write-Host 'KEYTOOL_PUBLIC_METADATA=PASS'
    Write-Host 'PLAY_FINGERPRINT_POLICY_MATCH=PASS'
    Write-Host 'DOMESTIC_FINGERPRINT_POLICY_MATCH=PASS'
    Write-Host 'CROSS_MACHINE_SIGNING_RECOVERY=PASS (run this on the original laptop or independent machine)'
}
finally {
    if (Test-Path -LiteralPath $ExtractionDirectory) { Remove-Item -LiteralPath $ExtractionDirectory -Recurse -Force }
    $recoverySecret = $null
    $playPassword = $null
    $domesticPassword = $null
}
