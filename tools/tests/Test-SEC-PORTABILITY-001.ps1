[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$securityPath = Join-Path $PSScriptRoot '..\PortableSigningVault.Security.ps1'
$newVaultScript = Join-Path $PSScriptRoot '..\New-PortableSigningVault.ps1'
$testVaultScript = Join-Path $PSScriptRoot '..\Test-PortableSigningVault.ps1'
. $securityPath

$pwshCommand = Get-Command pwsh -ErrorAction SilentlyContinue
if ($null -eq $pwshCommand) {
    $pwshCommand = Get-Command powershell -ErrorAction SilentlyContinue
}
if ($null -eq $pwshCommand) {
    throw 'PowerShell is required for the targeted portability tests.'
}
$pwshPath = if ($pwshCommand.Source) { $pwshCommand.Source } else { $pwshCommand.Path }

$failures = [System.Collections.Generic.List[string]]::new()
$observedOutput = [System.Collections.Generic.List[string]]::new()
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('flowbreak-sec-portability-' + [guid]::NewGuid().ToString('N'))

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-NotContains {
    param(
        [AllowEmptyString()]
        [string]$Value,
        [Parameter(Mandatory = $true)]
        [string]$Forbidden
    )
    if ($Value.Contains($Forbidden)) {
        throw 'Secret marker appeared in observed output.'
    }
}

function Invoke-PowerShellScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,
        [string[]]$Arguments = @()
    )

    $fullArguments = @('-NoProfile', '-NonInteractive', '-File', $ScriptPath) + $Arguments
    $output = & $pwshPath @fullArguments 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output -join "`n")
    }
}

function Invoke-TransportProbe {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ReferenceSwitch,
        [Parameter(Mandatory = $true)]
        [string]$Prefix,
        [Parameter(Mandatory = $true)]
        [string]$SecretMarker,
        [Parameter(Mandatory = $true)]
        [string]$ObservationPath,
        [Parameter(Mandatory = $true)]
        [string]$ChildScriptPath
    )

    $binding = New-SecretEnvironmentBinding -Prefix $Prefix -Secret $SecretMarker
    try {
        $arguments = @(
            '-NoProfile',
            '-NonInteractive',
            '-File',
            $ChildScriptPath,
            $ObservationPath,
            $binding.Name,
            ('reference=' + $ReferenceSwitch),
            $binding.Name
        )
        $stdout = Invoke-SecretSafeProcess `
            -Executable $pwshPath `
            -Arguments $arguments `
            -SecretEnvironment $binding.Environment `
            -Action 'synthetic secret transport probe'
        $observedOutput.Add($stdout)

        $observation = Get-Content -Raw -LiteralPath $ObservationPath | ConvertFrom-Json
        $childArguments = @($observation.arguments) -join ' '
        Assert-True ($observation.environmentPresent -eq $true) 'Child did not receive the secret environment binding.'
        Assert-True ($childArguments.Contains($ReferenceSwitch)) 'Child did not receive the safe password reference switch.'
        Assert-True ($childArguments.Contains($binding.Name)) 'Child did not receive the safe environment variable name.'
        Assert-NotContains -Value $childArguments -Forbidden $SecretMarker
        Assert-NotContains -Value $stdout -Forbidden $SecretMarker
    } finally {
        Clear-SecretEnvironmentBinding -Environment $binding.Environment
    }
    Assert-True ($binding.Environment.Count -eq 0) 'Secret environment binding was not cleared after success.'
}

function Run-Test {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Body
    )

    try {
        & $Body
        Write-Output ('PASS ' + $Name)
    } catch {
        $failures.Add($Name)
        Write-Output ('FAIL ' + $Name)
    }
}

try {
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    $childScript = Join-Path $testRoot 'observe-child.ps1'
    @'
param(
    [string]$ObservationPath,
    [string]$SecretEnvironmentName
)

$secret = [Environment]::GetEnvironmentVariable($SecretEnvironmentName)
[ordered]@{
    environmentPresent = -not [string]::IsNullOrEmpty($secret)
    arguments = @($args)
} | ConvertTo-Json -Compress | Set-Content -LiteralPath $ObservationPath -Encoding UTF8 -NoNewline
Write-Output 'CHILD_TRANSPORT_OK'
'@ | Set-Content -LiteralPath $childScript -Encoding UTF8 -NoNewline

    $failureChildScript = Join-Path $testRoot 'failing-child.ps1'
    "exit 23`n" | Set-Content -LiteralPath $failureChildScript -Encoding UTF8 -NoNewline

    $newText = Get-Content -Raw -LiteralPath $newVaultScript
    $testText = Get-Content -Raw -LiteralPath $testVaultScript
    $securityText = Get-Content -Raw -LiteralPath $securityPath

    Run-Test 'safe reference transport hides recovery secret from child arguments' {
        Invoke-TransportProbe `
            -ReferenceSwitch '-storepass:env' `
            -Prefix 'FLOWBREAK_RECOVERY_SECRET_' `
            -SecretMarker 'synthetic-recovery-secret-marker-do-not-print' `
            -ObservationPath (Join-Path $testRoot 'recovery-observation.json') `
            -ChildScriptPath $childScript
    }

    Run-Test 'safe reference transport hides JKS store password from child arguments' {
        Invoke-TransportProbe `
            -ReferenceSwitch '-storepass:env' `
            -Prefix 'FLOWBREAK_JKS_STOREPASS_' `
            -SecretMarker 'synthetic-jks-store-password-marker-do-not-print' `
            -ObservationPath (Join-Path $testRoot 'storepass-observation.json') `
            -ChildScriptPath $childScript
    }

    Run-Test 'safe reference transport supports JKS key password form' {
        Invoke-TransportProbe `
            -ReferenceSwitch '-keypass:env' `
            -Prefix 'FLOWBREAK_JKS_KEYPASS_' `
            -SecretMarker 'synthetic-jks-key-password-marker-do-not-print' `
            -ObservationPath (Join-Path $testRoot 'keypass-observation.json') `
            -ChildScriptPath $childScript
    }

    Run-Test 'secret environment binding is cleared after child failure' {
        $marker = 'synthetic-failure-secret-marker-do-not-print'
        $binding = New-SecretEnvironmentBinding -Prefix 'FLOWBREAK_FAILURE_SECRET_' -Secret $marker
        $caught = $false
        $errorText = ''
        try {
            try {
                [void](Invoke-SecretSafeProcess `
                    -Executable $pwshPath `
                    -Arguments @('-NoProfile', '-NonInteractive', '-File', $failureChildScript) `
                    -SecretEnvironment $binding.Environment `
                    -Action 'synthetic failing transport probe')
            } catch {
                $caught = $true
                $errorText = $_.Exception.Message
            }
        } finally {
            Clear-SecretEnvironmentBinding -Environment $binding.Environment
        }
        Assert-True $caught 'Failing child process did not produce an error.'
        Assert-True ($binding.Environment.Count -eq 0) 'Secret environment binding was not cleared after child failure.'
        Assert-NotContains -Value $errorText -Forbidden $marker
    }

    Run-Test 'production vault creation fails closed without a safe backend' {
        $result = Invoke-PowerShellScript -ScriptPath $newVaultScript -Arguments @('-SigningRoot', $testRoot)
        $observedOutput.Add($result.Output)
        Assert-True ($result.ExitCode -ne 0) 'Vault creation unexpectedly succeeded without a safe backend.'
        Assert-True ($result.Output -match 'PORTABLE_VAULT_IMPLEMENTATION = BLOCKED_PENDING_SAFE_TOOL') 'Missing safe-backend fail-closed result.'
    }

    Run-Test 'explicit untrusted archive tool is rejected' {
        $fakeTool = Join-Path $testRoot 'fake-archive.exe'
        'synthetic executable placeholder' | Set-Content -LiteralPath $fakeTool -Encoding UTF8 -NoNewline
        $result = Invoke-PowerShellScript -ScriptPath $newVaultScript -Arguments @('-SigningRoot', $testRoot, '-ArchiveToolPath', $fakeTool)
        $observedOutput.Add($result.Output)
        Assert-True ($result.ExitCode -ne 0) 'Untrusted archive tool was accepted.'
        Assert-True ($result.Output -match 'UNTRUSTED_ARCHIVER_FALLBACK = REJECTED') 'Untrusted archive rejection was not explicit.'
    }

    Run-Test 'vault restore fails closed before prompting for secrets' {
        $vault = Join-Path $testRoot 'synthetic-vault.pending'
        $policy = Join-Path $testRoot 'synthetic-policy.json'
        $extraction = Join-Path $testRoot 'should-not-be-created'
        'placeholder' | Set-Content -LiteralPath $vault -Encoding UTF8 -NoNewline
        '{}' | Set-Content -LiteralPath $policy -Encoding UTF8 -NoNewline
        $result = Invoke-PowerShellScript -ScriptPath $testVaultScript -Arguments @(
            '-VaultPath', $vault,
            '-PolicyPath', $policy,
            '-ExtractionDirectory', $extraction
        )
        $observedOutput.Add($result.Output)
        Assert-True ($result.ExitCode -ne 0) 'Vault restore unexpectedly proceeded without a safe backend.'
        Assert-True ($result.Output -match 'PORTABLE_VAULT_IMPLEMENTATION = BLOCKED_PENDING_SAFE_TOOL') 'Vault restore did not fail closed.'
        Assert-True (-not (Test-Path -LiteralPath $extraction)) 'Fail-closed restore created an extraction directory.'
    }

    Run-Test 'portable scripts contain no legacy secret arguments or untrusted fallbacks' {
        Assert-True (-not ($newText -match '(?i)-p\s*\$recoverySecret')) 'Legacy recovery secret argument remains in vault creation script.'
        Assert-True (-not ($testText -match '(?i)-storepass[\s''\",]+\$')) 'Legacy JKS password argument remains in vault test script.'
        Assert-True (-not ($newText -match '(?i)Get-Command\s+.*7z|Common Files\\Adobe')) 'Untrusted archiver fallback remains in vault creation script.'
        Assert-True (-not ($testText -match '(?i)Get-Command\s+.*7z|Common Files\\Adobe')) 'Untrusted archiver fallback remains in vault test script.'
        Assert-True (-not ($newText -match 'TEMPORARY_SIGNING_WORKSTATION')) 'Obsolete workstation role remains in vault creation script.'
        Assert-True (-not ($testText -match 'TEMPORARY_SIGNING_WORKSTATION')) 'Obsolete workstation role remains in vault test script.'
        Assert-True ($securityText -match '(?i)-storepass:env') 'Safe keytool store-password transport is missing.'
        Assert-True ($securityText -match '(?i)EnvironmentVariables\[\$name\]') 'Secret environment transport is missing.'
    }

    foreach ($output in $observedOutput) {
        foreach ($marker in @(
            'synthetic-recovery-secret-marker-do-not-print',
            'synthetic-jks-store-password-marker-do-not-print',
            'synthetic-jks-key-password-marker-do-not-print',
            'synthetic-failure-secret-marker-do-not-print'
        )) {
            Assert-NotContains -Value $output -Forbidden $marker
        }
    }
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

if ($failures.Count -gt 0) {
    Write-Output ('TARGETED_TESTS=FAIL count=' + $failures.Count)
    foreach ($failure in $failures) {
        Write-Output ('FAILED_TEST=' + $failure)
    }
    exit 1
}

Write-Output 'TARGETED_TESTS=PASS'
Write-Output 'RECOVERY_SECRET_IN_PROCESS_ARGS=NO'
Write-Output 'JKS_PASSWORD_IN_PROCESS_ARGS=NO'
Write-Output 'UNTRUSTED_ARCHIVER_FALLBACK=NO'
Write-Output 'SECRET_LOGGING=NO'
Write-Output 'FAIL_CLOSED_WHEN_SAFE_BACKEND_UNAVAILABLE=YES'
