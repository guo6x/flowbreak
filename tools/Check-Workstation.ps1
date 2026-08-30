[CmdletBinding()]
param(
    # The script is normally run from a clone with no argument.  An explicit
    # root is useful when the checkout lives on a different drive or when the
    # script is invoked by another read-only audit tool.
    [string]$RepoRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$results = New-Object 'System.Collections.Generic.List[object]'
$failures = New-Object 'System.Collections.Generic.List[string]'
$warnings = New-Object 'System.Collections.Generic.List[string]'

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('PASS', 'WARN', 'FAIL')][string]$Status,
        [Parameter(Mandatory = $true)][string]$Details
    )

    $record = [pscustomobject]@{
        Name = $Name
        Status = $Status
        Details = $Details
    }
    [void]$results.Add($record)
    if ($Status -eq 'FAIL') {
        [void]$failures.Add(('{0}: {1}' -f $Name, $Details))
    }
    elseif ($Status -eq 'WARN') {
        [void]$warnings.Add(('{0}: {1}' -f $Name, $Details))
    }
    Write-Output ('{0} {1}: {2}' -f $Status, $Name, $Details)
}

function Resolve-Executable {
    param(
        [Parameter(Mandatory = $true)][string[]]$Names,
        [string[]]$CandidatePaths = @()
    )

    foreach ($name in $Names) {
        $commandInfo = Get-Command -Name $name -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandType -eq 'Application' -or $_.CommandType -eq 'ExternalScript' } |
            Select-Object -First 1
        if ($null -ne $commandInfo) {
            $source = [string]$commandInfo.Source
            if ([string]::IsNullOrWhiteSpace($source)) {
                $source = [string]$commandInfo.Definition
            }
            if (-not [string]::IsNullOrWhiteSpace($source) -and
                (Test-Path -LiteralPath $source -PathType Leaf)) {
                return [pscustomobject]@{
                    Name = $name
                    Path = (Resolve-Path -LiteralPath $source).Path
                    FromPath = $true
                }
            }
        }
    }

    foreach ($candidate in $CandidatePaths) {
        if (-not [string]::IsNullOrWhiteSpace([string]$candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return [pscustomobject]@{
                Name = (Split-Path -Leaf $candidate)
                Path = (Resolve-Path -LiteralPath $candidate).Path
                FromPath = $false
            }
        }
    }
    return $null
}

function Read-ToolVersion {
    param(
        [Parameter(Mandatory = $true)]$Tool,
        [string[]]$Arguments = @('--version'),
        [string]$ExpectedPattern = ''
    )

    try {
        # Only version flags are sent to tools.  No auth/status/configuration
        # commands are run, and stderr is reduced to the first short line.
        # Java (and a few Windows tools) writes its normal version banner to
        # stderr.  Windows PowerShell turns redirected native stderr into an
        # ErrorRecord when ErrorActionPreference=Stop, so temporarily allow
        # that stream through and restore the caller's preference afterwards.
        $savedErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $lines = @(& $Tool.Path @Arguments 2>&1 | ForEach-Object { $_.ToString() })
        }
        finally {
            $ErrorActionPreference = $savedErrorActionPreference
        }
        $nonEmptyLines = $lines |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedPattern)) {
            # Select only a known version-banner shape.  This prevents a
            # launcher banner such as "Picked up JAVA_TOOL_OPTIONS: ..." from
            # being echoed (that value could contain a credential).
            $line = $nonEmptyLines |
                Where-Object { ([string]$_) -match $ExpectedPattern } |
                Select-Object -First 1
        }
        else {
            $line = $nonEmptyLines |
                Where-Object { ([string]$_) -notmatch '(?i)(password|token|secret|credential|picked up .*_OPTIONS)' } |
                Select-Object -First 1
        }
        if ($null -eq $line) {
            return 'version unreadable'
        }
        $text = ([string]$line).Trim() -replace '\s+', ' '
        if ($text.Length -gt 180) {
            $text = $text.Substring(0, 180)
        }
        return $text
    }
    catch {
        return 'version unreadable (command failed)'
    }
}

function Get-VersionTriple {
    param([string]$Text)

    $match = [regex]::Match($Text, '(?<!\d)(\d+)(?:\.(\d+))?(?:\.(\d+))?')
    if (-not $match.Success) {
        return $null
    }
    $minor = 0
    $patch = 0
    if ($match.Groups[2].Success) { $minor = [int]$match.Groups[2].Value }
    if ($match.Groups[3].Success) { $patch = [int]$match.Groups[3].Value }
    return [pscustomobject]@{
        Major = [int]$match.Groups[1].Value
        Minor = $minor
        Patch = $patch
        Text = $match.Value
    }
}

function Test-VersionAtLeast {
    param(
        $Actual,
        [int]$Major,
        [int]$Minor = 0,
        [int]$Patch = 0
    )

    if ($null -eq $Actual) { return $false }
    if ($Actual.Major -ne $Major) { return ($Actual.Major -gt $Major) }
    if ($Actual.Minor -ne $Minor) { return ($Actual.Minor -gt $Minor) }
    return ($Actual.Patch -ge $Patch)
}

function Find-FileInDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string[]]$Basenames
    )

    foreach ($basename in $Basenames) {
        $candidate = Join-Path $Directory $basename
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-RepositoryRoot {
    param([string]$RequestedRoot, [string]$ScriptDirectory)

    $candidate = $RequestedRoot
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = Join-Path $ScriptDirectory '..'
    }
    try {
        return (Resolve-Path -LiteralPath $candidate -ErrorAction Stop).Path
    }
    catch {
        return $null
    }
}

$scriptPath = $PSCommandPath
if ([string]::IsNullOrWhiteSpace($scriptPath)) {
    $scriptPath = $MyInvocation.MyCommand.Path
}
$scriptDirectory = Split-Path -Parent $scriptPath
$repoRoot = Get-RepositoryRoot $RepoRoot $scriptDirectory

Write-Output 'FlowBreak workstation check (read-only; no installation or device probing)'
Write-Output ('Script: {0}' -f $scriptPath)

if ([string]::IsNullOrWhiteSpace([string]$repoRoot)) {
    Add-Check 'repository root' 'FAIL' 'could not resolve the clone root; pass -RepoRoot <path>'
    Write-Output ''
    Write-Output 'WORKSTATION_READY = NO'
    exit 1
}
Add-Check 'repository root' 'PASS' $repoRoot

# PowerShell itself is the host requested by the recovery command.
$psVersion = [string]$PSVersionTable.PSVersion
if ($PSVersionTable.PSVersion.Major -ge 5) {
    Add-Check 'PowerShell' 'PASS' ('{0} (Windows PowerShell 5.1+ / PowerShell 7+ supported)' -f $psVersion)
}
else {
    Add-Check 'PowerShell' 'FAIL' ('{0}; PowerShell 5.1 or newer is required' -f $psVersion)
}

# Package metadata is read only to derive the checked-in Node engine and to
# verify the lockfile used by npm ci.  No environment inventory is enumerated.
$packagePath = Join-Path $repoRoot 'app\package.json'
$package = $null
if (Test-Path -LiteralPath $packagePath -PathType Leaf) {
    try {
        $package = Get-Content -Raw -LiteralPath $packagePath | ConvertFrom-Json
        Add-Check 'package.json' 'PASS' $packagePath
    }
    catch {
        Add-Check 'package.json' 'FAIL' 'file exists but is not valid JSON'
    }
}
else {
    Add-Check 'package.json' 'FAIL' ('missing: {0}' -f $packagePath)
}

if ($null -ne $package) {
    $requiredNpmScripts = @('build', 'test', 'test:provenance', 'test:release')
    $missingNpmScripts = @()
    foreach ($scriptName in $requiredNpmScripts) {
        if ($null -eq $package.scripts -or $null -eq $package.scripts.$scriptName) {
            $missingNpmScripts += $scriptName
        }
    }
    if ($missingNpmScripts.Count -eq 0) {
        Add-Check 'npm scripts' 'PASS' 'build, test, test:provenance, and test:release are declared'
    }
    else {
        Add-Check 'npm scripts' 'FAIL' ('missing package.json scripts: {0}' -f ($missingNpmScripts -join ', '))
    }
}

$lockPath = Join-Path $repoRoot 'app\package-lock.json'
if (Test-Path -LiteralPath $lockPath -PathType Leaf) {
    $lockItem = Get-Item -LiteralPath $lockPath
    if ($lockItem.Length -gt 0) {
        Add-Check 'package-lock.json' 'PASS' ('present ({0} bytes)' -f $lockItem.Length)
    }
    else {
        Add-Check 'package-lock.json' 'FAIL' 'file is empty; npm ci cannot be reproduced'
    }
}
else {
    Add-Check 'package-lock.json' 'FAIL' ('missing: {0}' -f $lockPath)
}

# Resolve and version-check the host tools.  Version flags are local and do
# not contact GitHub, Android devices, or signing services.
$git = Resolve-Executable @('git.exe', 'git')
if ($null -eq $git) {
    Add-Check 'git' 'FAIL' 'executable not found on PATH'
}
else {
    $gitVersion = Read-ToolVersion $git @('--version') '^git version\s+\S+'
    Add-Check 'git' 'PASS' ('{0}; path={1}' -f $gitVersion, $git.Path)
    try {
        $gitRoot = (& $git.Path '-C' $repoRoot 'rev-parse' '--show-toplevel' 2>$null | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($gitRoot)) {
            Add-Check 'git repository' 'FAIL' 'resolved root is not a Git worktree'
        }
        else {
            Add-Check 'git repository' 'PASS' $gitRoot
        }
    }
    catch {
        Add-Check 'git repository' 'FAIL' 'git rev-parse could not read the clone'
    }
}

$node = Resolve-Executable @('node.exe', 'node')
$nodeRequirementMajor = 22
$nodeRequirementMinor = 22
$nodeRequirementPatch = 0
if ($null -ne $package -and $null -ne $package.engines -and
    ([string]$package.engines.node) -match '>=\s*(\d+)\.(\d+)\.(\d+)') {
    $nodeRequirementMajor = [int]$Matches[1]
    $nodeRequirementMinor = [int]$Matches[2]
    $nodeRequirementPatch = [int]$Matches[3]
}
if ($null -eq $node) {
    Add-Check 'node' 'FAIL' ('missing; required >= {0}.{1}.{2} and < 23' -f $nodeRequirementMajor, $nodeRequirementMinor, $nodeRequirementPatch)
}
else {
    $nodeVersionText = Read-ToolVersion $node @('--version') '^v?\d+\.\d+'
    $nodeVersion = Get-VersionTriple $nodeVersionText
    $nodeInRange = ($null -ne $nodeVersion -and
        (Test-VersionAtLeast $nodeVersion $nodeRequirementMajor $nodeRequirementMinor $nodeRequirementPatch) -and
        $nodeVersion.Major -lt 23)
    if ($nodeInRange) {
        Add-Check 'node' 'PASS' ('{0}; path={1}' -f $nodeVersionText, $node.Path)
    }
    else {
        Add-Check 'node' 'FAIL' ('{0}; required >= {1}.{2}.{3} and < 23' -f $nodeVersionText, $nodeRequirementMajor, $nodeRequirementMinor, $nodeRequirementPatch)
    }
}

$npm = Resolve-Executable @('npm.cmd', 'npm.exe', 'npm.ps1', 'npm')
if ($null -eq $npm) {
    Add-Check 'npm' 'FAIL' 'executable not found; npm ci/build/test cannot run'
}
else {
    $npmVersionText = Read-ToolVersion $npm @('--version') '^v?\d+\.\d+'
    $npmVersion = Get-VersionTriple $npmVersionText
    if ($null -ne $npmVersion) {
        Add-Check 'npm' 'PASS' ('{0}; path={1}' -f $npmVersionText, $npm.Path)
    }
    else {
        Add-Check 'npm' 'FAIL' ('{0}; npm version could not be read' -f $npmVersionText)
    }
}

# JAVA_HOME is intentionally the only Java-related environment value read.
# No secret-bearing environment variables are inspected.
$javaHome = [string]$env:JAVA_HOME
$javaHomePath = $null
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    Add-Check 'JAVA_HOME' 'FAIL' 'not set; point it to a JDK 21 installation root'
}
elseif (-not (Test-Path -LiteralPath $javaHome -PathType Container)) {
    Add-Check 'JAVA_HOME' 'FAIL' ('path does not exist: {0}' -f $javaHome)
}
else {
    $javaHomePath = (Resolve-Path -LiteralPath $javaHome).Path
    Add-Check 'JAVA_HOME' 'PASS' $javaHomePath
}

$javaCandidates = @()
if ($null -ne $javaHomePath) {
    $javaCandidates += (Join-Path $javaHomePath 'bin\java.exe')
    $javaCandidates += (Join-Path $javaHomePath 'bin\java')
}
$java = $null
if ($null -ne $javaHomePath) {
    $javaHomeExecutable = Find-FileInDirectory (Join-Path $javaHomePath 'bin') @('java.exe', 'java')
    if ($null -ne $javaHomeExecutable) {
        $java = [pscustomobject]@{ Name = 'java'; Path = $javaHomeExecutable; FromPath = $false }
    }
}
if ($null -eq $java) {
    $java = Resolve-Executable @('java.exe', 'java') $javaCandidates
}
if ($null -eq $java) {
    Add-Check 'java' 'FAIL' 'JDK java executable not found'
}
else {
    $javaVersionText = Read-ToolVersion $java @('-version') '(?i)^(?:openjdk|java)\s+version\s+["'']?\d+'
    $javaVersion = Get-VersionTriple $javaVersionText
    if ($null -ne $javaVersion -and $javaVersion.Major -eq 21) {
        Add-Check 'java' 'PASS' ('{0}; path={1}' -f $javaVersionText, $java.Path)
    }
    else {
        Add-Check 'java' 'FAIL' ('{0}; JDK 21 is required' -f $javaVersionText)
    }
    if ($null -ne $javaHomePath -and
        -not $java.Path.StartsWith((Join-Path $javaHomePath 'bin'), [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-Check 'JAVA_HOME/PATH alignment' 'WARN' ('java resolves outside JAVA_HOME; Gradle will use JAVA_HOME: {0}' -f $javaHomePath)
    }
}

$keytoolCandidates = @()
if ($null -ne $javaHomePath) {
    $keytoolCandidates += (Join-Path $javaHomePath 'bin\keytool.exe')
    $keytoolCandidates += (Join-Path $javaHomePath 'bin\keytool')
}
$keytool = $null
if ($null -ne $javaHomePath) {
    $keytoolHomeExecutable = Find-FileInDirectory (Join-Path $javaHomePath 'bin') @('keytool.exe', 'keytool')
    if ($null -ne $keytoolHomeExecutable) {
        $keytool = [pscustomobject]@{ Name = 'keytool'; Path = $keytoolHomeExecutable; FromPath = $false }
    }
}
if ($null -eq $keytool) {
    $keytool = Resolve-Executable @('keytool.exe', 'keytool') $keytoolCandidates
}
if ($null -eq $keytool) {
    Add-Check 'keytool' 'FAIL' 'JDK keytool executable not found (required for public certificate verification)'
}
else {
    $keytoolVersionText = Read-ToolVersion $keytool @('-J-Duser.language=en', '-J-Duser.country=US', '-version') '(?i)^keytool\s+\d+'
    $keytoolVersion = Get-VersionTriple $keytoolVersionText
    if ($null -ne $keytoolVersion -and $keytoolVersion.Major -eq 21) {
        Add-Check 'keytool' 'PASS' ('{0}; path={1}' -f $keytoolVersionText, $keytool.Path)
    }
    else {
        Add-Check 'keytool' 'FAIL' ('{0}; keytool from JDK 21 is required' -f $keytoolVersionText)
    }
}

# Android SDK resolution: prefer ANDROID_SDK_ROOT, then ANDROID_HOME, then
# the standard per-user location.  Only these two non-secret path variables
# are read; the script never enumerates the environment block.
$sdkRootEnv = [string]$env:ANDROID_SDK_ROOT
$sdkHomeEnv = [string]$env:ANDROID_HOME
$sdkRootPath = $null
$sdkRootSource = $null
$validSdkEnvPaths = @{}

if ([string]::IsNullOrWhiteSpace($sdkRootEnv)) {
    Add-Check 'ANDROID_SDK_ROOT' 'WARN' 'not set (the standard per-user SDK path will be tried)'
}
elseif (Test-Path -LiteralPath $sdkRootEnv -PathType Container) {
    $resolvedSdkRootEnv = (Resolve-Path -LiteralPath $sdkRootEnv).Path
    $validSdkEnvPaths['ANDROID_SDK_ROOT'] = $resolvedSdkRootEnv
    Add-Check 'ANDROID_SDK_ROOT' 'PASS' $resolvedSdkRootEnv
}
else {
    Add-Check 'ANDROID_SDK_ROOT' 'WARN' ('set but path does not exist: {0}' -f $sdkRootEnv)
}

if ([string]::IsNullOrWhiteSpace($sdkHomeEnv)) {
    Add-Check 'ANDROID_HOME' 'WARN' 'not set (ANDROID_SDK_ROOT or the standard per-user SDK path is sufficient)'
}
elseif (Test-Path -LiteralPath $sdkHomeEnv -PathType Container) {
    $resolvedSdkHomeEnv = (Resolve-Path -LiteralPath $sdkHomeEnv).Path
    $validSdkEnvPaths['ANDROID_HOME'] = $resolvedSdkHomeEnv
    Add-Check 'ANDROID_HOME' 'PASS' $resolvedSdkHomeEnv
}
else {
    Add-Check 'ANDROID_HOME' 'WARN' ('set but path does not exist: {0}' -f $sdkHomeEnv)
}

if ($validSdkEnvPaths.ContainsKey('ANDROID_SDK_ROOT')) {
    $sdkRootPath = $validSdkEnvPaths['ANDROID_SDK_ROOT']
    $sdkRootSource = 'ANDROID_SDK_ROOT'
}
elseif ($validSdkEnvPaths.ContainsKey('ANDROID_HOME')) {
    $sdkRootPath = $validSdkEnvPaths['ANDROID_HOME']
    $sdkRootSource = 'ANDROID_HOME'
}

$localAppData = [Environment]::GetFolderPath('LocalApplicationData')
$defaultSdkCandidates = @()
if (-not [string]::IsNullOrWhiteSpace($localAppData)) {
    $defaultSdkCandidates += (Join-Path $localAppData 'Android\Sdk')
}
if ($null -eq $sdkRootPath) {
    foreach ($defaultSdk in $defaultSdkCandidates) {
        if (Test-Path -LiteralPath $defaultSdk -PathType Container) {
            $sdkRootPath = (Resolve-Path -LiteralPath $defaultSdk).Path
            $sdkRootSource = 'standard per-user path'
            break
        }
    }
}

if ($null -eq $sdkRootPath) {
    Add-Check 'Android SDK' 'FAIL' 'SDK root not found; set ANDROID_SDK_ROOT or install/configure a per-user SDK'
}
else {
    Add-Check 'Android SDK' 'PASS' ('{0} (source={1})' -f $sdkRootPath, $sdkRootSource)
    if ($validSdkEnvPaths.ContainsKey('ANDROID_SDK_ROOT') -and
        $validSdkEnvPaths.ContainsKey('ANDROID_HOME') -and
        $validSdkEnvPaths['ANDROID_SDK_ROOT'] -ne $validSdkEnvPaths['ANDROID_HOME']) {
        Add-Check 'Android SDK env alignment' 'WARN' 'ANDROID_SDK_ROOT and ANDROID_HOME point to different directories; keep one canonical root'
    }
}

# Read the checked-in API levels; fall back to the documented values only if
# the variables file is unavailable, so this check remains useful during a
# partially copied checkout.
$compileSdk = 36
$targetSdk = 35
$variablesPath = Join-Path $repoRoot 'app\android\variables.gradle'
if (Test-Path -LiteralPath $variablesPath -PathType Leaf) {
    $variablesText = Get-Content -Raw -LiteralPath $variablesPath
    $compileMatch = [regex]::Match($variablesText, 'compileSdkVersion\s*=\s*(\d+)')
    $targetMatch = [regex]::Match($variablesText, 'targetSdkVersion\s*=\s*(\d+)')
    if ($compileMatch.Success) { $compileSdk = [int]$compileMatch.Groups[1].Value }
    if ($targetMatch.Success) { $targetSdk = [int]$targetMatch.Groups[1].Value }
}

if ($null -ne $sdkRootPath) {
    $compilePlatformPath = Join-Path $sdkRootPath ('platforms\android-{0}' -f $compileSdk)
    if (Test-Path -LiteralPath $compilePlatformPath -PathType Container) {
        Add-Check 'Android SDK platform' 'PASS' ('android-{0} (compileSdk)' -f $compileSdk)
    }
    else {
        Add-Check 'Android SDK platform' 'FAIL' ('missing platforms\android-{0} (compileSdk)' -f $compileSdk)
    }

    $targetPlatformPath = Join-Path $sdkRootPath ('platforms\android-{0}' -f $targetSdk)
    if (Test-Path -LiteralPath $targetPlatformPath -PathType Container) {
        Add-Check 'Android target platform' 'PASS' ('android-{0}' -f $targetSdk)
    }
    else {
        Add-Check 'Android target platform' 'WARN' ('platforms\android-{0} is not present; compileSdk {1} is the build requirement' -f $targetSdk, $compileSdk)
    }

    $buildToolsRoot = Join-Path $sdkRootPath 'build-tools'
    $buildToolRecords = @()
    if (Test-Path -LiteralPath $buildToolsRoot -PathType Container) {
        foreach ($buildToolDirectory in @(Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue)) {
            if ($buildToolDirectory.Name -match '^(\d+)\.(\d+)\.(\d+)$') {
                $buildToolRecords += [pscustomobject]@{
                    Name = $buildToolDirectory.Name
                    Version = [version]::new([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
                    Path = $buildToolDirectory.FullName
                }
            }
        }
    }
    $selectedBuildTools = $buildToolRecords | Sort-Object Version -Descending | Select-Object -First 1
    $minimumBuildTools = [version]'35.0.0'
    if ($null -eq $selectedBuildTools) {
        Add-Check 'Android build-tools' 'FAIL' ('no stable build-tools directory found under {0}' -f $buildToolsRoot)
    }
    elseif ($selectedBuildTools.Version -lt $minimumBuildTools) {
        Add-Check 'Android build-tools' 'FAIL' ('latest {0} is below required {1}+ for AGP/compileSdk {2}' -f $selectedBuildTools.Name, $minimumBuildTools, $compileSdk)
    }
    else {
        $aapt2 = Find-FileInDirectory $selectedBuildTools.Path @('aapt2.exe', 'aapt2')
        $apksigner = Find-FileInDirectory $selectedBuildTools.Path @('apksigner.bat', 'apksigner.exe', 'apksigner')
        $zipalign = Find-FileInDirectory $selectedBuildTools.Path @('zipalign.exe', 'zipalign')
        $missingBuildToolsBinaries = @()
        if ($null -eq $aapt2) { $missingBuildToolsBinaries += 'aapt2' }
        if ($null -eq $apksigner) { $missingBuildToolsBinaries += 'apksigner' }
        if ($null -eq $zipalign) { $missingBuildToolsBinaries += 'zipalign' }
        if ($missingBuildToolsBinaries.Count -gt 0) {
            Add-Check 'Android build-tools' 'FAIL' ('{0} is missing: {1}' -f $selectedBuildTools.Name, ($missingBuildToolsBinaries -join ', '))
        }
        else {
            Add-Check 'Android build-tools' 'PASS' ('{0}; aapt2/apksigner/zipalign present' -f $selectedBuildTools.Name)
        }
    }

    $sdkManagerCandidates = @(
        (Join-Path $sdkRootPath 'cmdline-tools\latest\bin\sdkmanager.bat'),
        (Join-Path $sdkRootPath 'cmdline-tools\latest\bin\sdkmanager'),
        (Join-Path $sdkRootPath 'tools\bin\sdkmanager.bat'),
        (Join-Path $sdkRootPath 'tools\bin\sdkmanager')
    )
    $sdkManager = Resolve-Executable @('sdkmanager.bat', 'sdkmanager') $sdkManagerCandidates
    if ($null -ne $sdkManager) {
        Add-Check 'sdkmanager' 'PASS' ('present; path={0} (not executed)' -f $sdkManager.Path)
    }
    else {
        Add-Check 'sdkmanager' 'WARN' 'not found; SDK may still be usable through Android Studio, but package repair will require command-line tools'
    }
}

# ADB is deliberately a presence-only check.  The current phase forbids any
# adb/scrcpy/device command because no phone is available.
$adbCandidates = @()
if ($null -ne $sdkRootPath) {
    $adbCandidates += (Join-Path $sdkRootPath 'platform-tools\adb.exe')
    $adbCandidates += (Join-Path $sdkRootPath 'platform-tools\adb')
}
$adb = Resolve-Executable @('adb.exe', 'adb') $adbCandidates
if ($null -ne $adb) {
    Add-Check 'adb' 'WARN' ('executable present at {0}; version/device probe intentionally skipped in PRE_WORKSTATION_MIGRATION_PREP' -f $adb.Path)
}
else {
    Add-Check 'adb' 'WARN' 'not found; required only when resuming connected-device gates D/E/F'
}

# Gradle is intentionally not invoked.  The checked-in wrapper is the
# reproducible build entry point and downloads no distribution during this
# read-only check.
$gradleRoot = Join-Path $repoRoot 'app\android'
$wrapperPaths = @(
    (Join-Path $gradleRoot 'gradlew.bat'),
    (Join-Path $gradleRoot 'gradlew'),
    (Join-Path $gradleRoot 'gradle\wrapper\gradle-wrapper.jar'),
    (Join-Path $gradleRoot 'gradle\wrapper\gradle-wrapper.properties')
)
$missingWrapper = @($wrapperPaths | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
if ($missingWrapper.Count -gt 0) {
    Add-Check 'Gradle wrapper' 'FAIL' ('missing: {0}' -f (($missingWrapper | ForEach-Object { $_.Substring($repoRoot.Length + 1) }) -join ', '))
}
else {
    $wrapperPropertiesPath = Join-Path $gradleRoot 'gradle\wrapper\gradle-wrapper.properties'
    $wrapperProperties = Get-Content -Raw -LiteralPath $wrapperPropertiesPath
    $distribution = ([regex]::Match($wrapperProperties, 'distributionUrl=.*gradle-([^\\/]+)-bin\.zip')).Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($distribution)) {
        Add-Check 'Gradle wrapper' 'WARN' 'wrapper files present; distribution version could not be parsed'
    }
    elseif ($distribution -ne '8.13') {
        Add-Check 'Gradle wrapper' 'WARN' ('wrapper resolves Gradle {0}; repository guidance currently expects 8.13' -f $distribution)
    }
    else {
        Add-Check 'Gradle wrapper' 'PASS' 'gradlew/gradlew.bat, wrapper jar/properties present; Gradle 8.13'
    }
}

# 7-Zip is needed for the future portable signing vault only.  Presence is
# useful inventory evidence, but this script never launches it and never
# creates/opens a vault while Gate G is frozen.
$sevenZipCandidates = @(
    'C:\Program Files\7-Zip\7z.exe',
    'C:\Program Files\7-Zip\7zz.exe',
    'C:\Program Files (x86)\7-Zip\7z.exe',
    'C:\Program Files (x86)\7-Zip\7zz.exe'
)
$sevenZip = Resolve-Executable @('7z.exe', '7z', '7zz.exe', '7zz') $sevenZipCandidates
if ($null -ne $sevenZip) {
    Add-Check '7-Zip' 'PASS' ('present at {0}; not executed (Gate G is frozen)' -f $sevenZip.Path)
}
else {
    Add-Check '7-Zip' 'WARN' 'not found; required before future portable-vault work, not required for offline coding'
}

# gh is useful for PR/CI inspection but is not needed for a local build.  Do
# not run `gh auth status`: that could expose credential state or contact the
# network.
$gh = Resolve-Executable @('gh.exe', 'gh')
if ($null -ne $gh) {
    $ghVersionText = Read-ToolVersion $gh @('--version') '(?i)^gh\s+version\s+\S+'
    Add-Check 'GitHub CLI (gh)' 'PASS' ('{0}; path={1}; auth not inspected' -f $ghVersionText, $gh.Path)
}
else {
    Add-Check 'GitHub CLI (gh)' 'WARN' 'not found; optional for local build, needed for convenient PR/CI operations'
}

Write-Output ''
Write-Output ('Checks: {0} total, {1} warning(s), {2} failure(s)' -f $results.Count, $warnings.Count, $failures.Count)
if ($failures.Count -gt 0) {
    Write-Output 'Missing/failed required items:'
    foreach ($failure in $failures) {
        Write-Output ('- {0}' -f $failure)
    }
}
if ($warnings.Count -gt 0) {
    Write-Output 'Warnings (non-blocking for offline recovery; resolve before the related gate):'
    foreach ($warning in $warnings) {
        Write-Output ('- {0}' -f $warning)
    }
}

$ready = if ($failures.Count -eq 0) { 'YES' } else { 'NO' }
Write-Output ('WORKSTATION_READY = {0}' -f $ready)
if ($ready -eq 'NO') {
    exit 1
}
exit 0
