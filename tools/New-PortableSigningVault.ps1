[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SigningRoot,
    [string]$PolicyPath = (Join-Path $PSScriptRoot '..\app\release-signing-policy.json'),
    [string]$OutputDirectory = '',
    [string]$ArchiveName = 'FlowBreak-production-signing-vault',
    [string]$ArchiveToolPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'PortableSigningVault.Security.ps1')

# No mature portable encryption/archive backend is installed or approved in
# this phase.  Fail before policy, key, clipboard, or secret handling.
Assert-PortableVaultBackendUnavailable -RequestedArchiveToolPath $ArchiveToolPath
