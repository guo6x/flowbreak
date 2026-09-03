[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$VaultPath,
    [Parameter(Mandatory = $true)]
    [string]$PolicyPath,
    [string]$ExtractionDirectory = (Join-Path $env:TEMP ('flowbreak-vault-restore-' + [guid]::NewGuid().ToString('N'))),
    [string]$ArchiveToolPath = '',
    [string]$KeytoolPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'PortableSigningVault.Security.ps1')

# Fail before prompting for recovery/JKS passwords or creating an extraction
# directory.  A future backend must use Invoke-SecretSafeProcess and
# Invoke-KeytoolPublicMetadata; literal password arguments are not allowed.
Assert-PortableVaultBackendUnavailable -RequestedArchiveToolPath $ArchiveToolPath
