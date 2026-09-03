Set-StrictMode -Version Latest

function Assert-PortableVaultBackendUnavailable {
    [CmdletBinding()]
    param(
        [string]$RequestedArchiveToolPath = ''
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedArchiveToolPath)) {
        throw 'UNTRUSTED_ARCHIVER_FALLBACK = REJECTED; no archive executable is accepted until a safe portable backend is approved.'
    }

    throw 'PORTABLE_VAULT_IMPLEMENTATION = BLOCKED_PENDING_SAFE_TOOL'
}

function New-SecretEnvironmentBinding {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prefix,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Secret
    )

    if ($Prefix -notmatch '^[A-Za-z_][A-Za-z0-9_]*_$') {
        throw 'Secret environment binding prefix is invalid.'
    }

    $name = $Prefix + [guid]::NewGuid().ToString('N')
    $environment = @{}
    $environment[$name] = $Secret
    return [pscustomobject]@{
        Name = $name
        Environment = $environment
    }
}

function Clear-SecretEnvironmentBinding {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Environment
    )

    foreach ($name in @($Environment.Keys)) {
        $Environment[$name] = $null
    }
    $Environment.Clear()
}

function Invoke-SecretSafeProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [hashtable]$SecretEnvironment,
        [Parameter(Mandatory = $true)]
        [string]$Action
    )

    $process = [System.Diagnostics.Process]::new()
    $startInfo = $null
    $secretNames = @($SecretEnvironment.Keys)
    try {
        if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
            throw "$Action executable was not found."
        }

        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $Executable
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        foreach ($argument in $Arguments) {
            [void]$startInfo.ArgumentList.Add([string]$argument)
        }
        foreach ($name in @($SecretEnvironment.Keys)) {
            if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
                throw "$Action secret environment variable name is invalid."
            }
            $value = $SecretEnvironment[$name]
            if ($null -eq $value) {
                throw "$Action secret environment variable value is missing."
            }
            $startInfo.EnvironmentVariables[$name] = [string]$value
        }

        $process.StartInfo = $startInfo
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "$Action failed (exit code $($process.ExitCode))."
        }
        return $stdout
    } finally {
        if ($null -ne $startInfo) {
            foreach ($name in $secretNames) {
                [void]$startInfo.EnvironmentVariables.Remove($name)
            }
        }
        $process.Dispose()
        Clear-SecretEnvironmentBinding -Environment $SecretEnvironment
        $startInfo = $null
        $stdout = $null
        $stderr = $null
    }
}

function Invoke-KeytoolPublicMetadata {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$KeytoolPath,
        [Parameter(Mandatory = $true)]
        [string]$KeystorePath,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$StorePassword
    )

    $binding = New-SecretEnvironmentBinding -Prefix 'FLOWBREAK_KEYTOOL_STOREPASS_' -Secret $StorePassword
    try {
        return Invoke-SecretSafeProcess `
            -Executable $KeytoolPath `
            -Arguments @('-list', '-v', '-keystore', $KeystorePath, '-storepass:env', $binding.Name) `
            -SecretEnvironment $binding.Environment `
            -Action 'keytool public metadata read'
    } finally {
        if ($null -ne $binding -and $null -ne $binding.Environment) {
            Clear-SecretEnvironmentBinding -Environment $binding.Environment
        }
        $StorePassword = $null
    }
}
