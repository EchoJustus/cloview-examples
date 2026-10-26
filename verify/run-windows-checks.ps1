<#
.SYNOPSIS
  cloview-examples verification on Windows: the fitness app on JavaFX, WPF and Flutter,
  and its acceptance benchmark.

.DESCRIPTION
  Runs every check of the example apps that cannot run in Claude's Linux sandbox and
  packages the results into verify\cloview-examples-windows-verification-results.zip
  (upload that file for review).

  Needs cloview-core cloned next to this repository (..\cloview-core): the apps use its
  modules, and the WPF host is built from its template. The template is copied to
  target\clr-host-project first, so the cloview-core checkout is never written to.

  Steps (each one is independent: a failing step, even one that throws, never stops the
  next one; a run stopped early with Ctrl+C still writes the summary and the zip):
    01  OS + toolchain diagnostics, the cloview-core checkout
                                                       -> out\01-env-check.json
    02  JVM: fitness unit tests + DOM-001 oracles, static check of apps\, JavaFX smoke
                                                       -> out\02-jvm.log, out\02-fitness-jvm-*.json
    03  CLR: stage cloview-core + app sources, build CloViewHost, fitness unit tests +
        DOM-001 oracles, WPF smoke                     -> out\03-clr.log, out\03-fitness-clr-*.json
    04  Dart: Flutter version check, ClojureDart compile, fitness widget test, shared
        tests + DOM-001 oracles                        -> out\04-dart.log, out\04-fitness-dart-*.json
    05  the baseline's Python checkers: package integrity, fixtures, --observed for each
        host's envelope                                -> out\05-baseline-checks.log
    00  step summary                                   -> out\00-summary.json

  Usage (from the repo root):
    powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1
    powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1 -SkipFlutter
    powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1 -SkipWpf -SkipFlutter   # JavaFX only
    powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1 -ClrTargetFramework net8.0-windows
    powershell -ExecutionPolicy Bypass -File .\verify\run-windows-checks.ps1 -FlutterUpgrade

  -FlutterUpgrade runs `flutter upgrade` first when the installed Flutter is older than
  3.35 (the minimum of ClojureDart 0.9.20261002). It changes your Flutter SDK, so it is
  never done without this switch.

  The helpers below are those of cloview-core's verify\run-windows-checks.ps1; keep the
  two copies in sync.

.NOTES
  ASCII only, Windows PowerShell 5.1 compatible (also runs under PowerShell 7).
#>
[CmdletBinding()]
param(
  [Alias('SkipJavaFX')][switch]$SkipJvm,   # step 02 (JVM tests, static check, JavaFX smoke)
  [Alias('SkipWpf')][switch]$SkipClr,       # step 03 (CloViewHost, CLR tests, WPF smoke)
  [switch]$SkipFlutter,                     # step 04
  [switch]$FlutterUpgrade,
  [string]$ClrTargetFramework = "",
  [switch]$NoZip
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# Environment changes below are undone at the end (the script may run inside your session).
$SavedEnv = @{}
foreach ($n in @('DOTNET_CLI_TELEMETRY_OPTOUT', 'DOTNET_NOLOGO', 'DOTNET_SKIP_FIRST_TIME_EXPERIENCE',
                 'JAVA_TOOL_OPTIONS', 'CLOVIEW_FITNESS_PREFIX')) {
  $SavedEnv[$n] = [Environment]::GetEnvironmentVariable($n, 'Process')
}
$SavedConsoleEncoding = $null
try { $SavedConsoleEncoding = [Console]::OutputEncoding } catch { }
function Restore-Session {
  foreach ($n in $SavedEnv.Keys) { [Environment]::SetEnvironmentVariable($n, $SavedEnv[$n], 'Process') }
  if ($null -ne $SavedConsoleEncoding) { try { [Console]::OutputEncoding = $SavedConsoleEncoding } catch { } }
}

# 00 - summary + zip. Called from the finally block at the end, so a run stopped early
# (Ctrl+C, or an exception between steps) still leaves 00-summary.json and the zip of what
# it produced, marked "aborted".
$RunCompleted = $false
function Complete-Run {
  if (($null -eq $Summary) -or ($null -eq $OutDir) -or -not (Test-Path -LiteralPath $OutDir)) { return }
  if (-not $RunCompleted -and -not $Summary.Contains('aborted')) {
    $Summary.aborted = 'stopped before the end (Ctrl+C or an unexpected error)'
  }
  $Summary.finished = (Get-Date).ToString('o')
  $Summary.expected_files = @('00-summary.json', '01-env-check.json',
                              '02-jvm.log', '02-fitness-jvm-summary.json', '02-fitness-jvm-business-results.json',
                              '02-fitness-jvm-smoke.json',
                              '03-clr.log', '03-fitness-clr-summary.json', '03-fitness-clr-business-results.json',
                              '03-fitness-clr-wpf-smoke.json',
                              '04-dart.log', '04-fitness-dart-summary.json', '04-fitness-dart-business-results.json',
                              '05-baseline-checks.log')
  $present = @(Get-ChildItem -LiteralPath $OutDir -File | Where-Object { $_.Name -ne '.gitkeep' } | ForEach-Object { $_.Name })
  $present += '00-summary.json'
  $Summary.present_files = @($present | Sort-Object -Unique)
  $Summary.missing_files = @($Summary.expected_files | Where-Object { $present -notcontains $_ })
  Write-Utf8File (Join-Path $OutDir '00-summary.json') ($Summary | ConvertTo-Json -Depth 6)

  Write-Host ''
  Write-Host '================ cloview-examples verification ================' -ForegroundColor Cyan
  foreach ($k in $Summary.steps.Keys) {
    $st = $Summary.steps[$k].status
    $color = 'Green'
    if ($st -eq 'failed') { $color = 'Red' } elseif ($st -ne 'ok') { $color = 'Yellow' }
    Write-Host ('  ' + $k.PadRight(30) + $st) -ForegroundColor $color
  }
  if ($Summary.Contains('aborted')) { Write-Host ('  aborted: ' + $Summary.aborted) -ForegroundColor Red }

  if (-not $NoZip) {
    if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
    $zipItems = @(Get-ChildItem -LiteralPath $OutDir -File | Where-Object { $_.Name -ne '.gitkeep' } | ForEach-Object { $_.FullName })
    Compress-Archive -LiteralPath $zipItems -DestinationPath $ZipPath -Force
    Write-Host ''
    Write-Host ('Results: ' + $ZipPath) -ForegroundColor Cyan
    Write-Host 'Upload this zip for review.'
  }
}

# Everything below runs inside try/catch/finally, so the environment is restored and the
# summary written even when the run is stopped with Ctrl+C (try does not open a new scope:
# functions and variables stay script-level). Inside a try, an exception skips the rest of
# the block, so every step below is its own try/catch and the helpers never throw.
try {
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_NOLOGO = '1'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'

# Native tools write UTF-8 (flutter, dart, python, and Java with the options below);
# Windows PowerShell 5.1 would otherwise decode their output with the OEM code page and
# log mojibake (the Stage 2-1 run logged 'Flutter 3.29.3 ?? channel stable').
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
try {
  [Console]::OutputEncoding = $Utf8NoBom
  $OutputEncoding = $Utf8NoBom
} catch { }
# (Python runs with -X utf8 on its command line: -I makes it ignore PYTHONUTF8.)
$javaOpts = @()
if (-not [string]::IsNullOrEmpty($env:JAVA_TOOL_OPTIONS)) { $javaOpts += $env:JAVA_TOOL_OPTIONS }
foreach ($prop in @('file.encoding', 'stdout.encoding', 'stderr.encoding', 'sun.stdout.encoding', 'sun.stderr.encoding')) {
  if (-not ("$env:JAVA_TOOL_OPTIONS" -match ('-D' + [regex]::Escape($prop) + '='))) { $javaOpts += ('-D' + $prop + '=UTF-8') }
}
$env:JAVA_TOOL_OPTIONS = ($javaOpts -join ' ')

$VerifyDir = $PSScriptRoot
# ProviderPath, not Path: on a UNC location Path is 'Microsoft.PowerShell.Core\FileSystem::\\...'
$RepoRoot = (Resolve-Path -LiteralPath (Join-Path $VerifyDir '..')).ProviderPath
$OutDir = Join-Path $VerifyDir 'out'
$ZipPath = Join-Path $VerifyDir 'cloview-examples-windows-verification-results.zip'

# fresh results, but keep the tracked verify\out\.gitkeep
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Get-ChildItem -LiteralPath $OutDir -Force | Where-Object { $_.Name -ne '.gitkeep' } | Remove-Item -Recurse -Force

# cloview-core: the sibling checkout that the deps.edn files (:local/root ../cloview-core)
# and this script use; it is only read, never written to
$CoreRoot = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot '..\cloview-core'))
$CoreOk = Test-Path -LiteralPath (Join-Path $CoreRoot 'modules\core\src\cloview\core\store.cljc')
$CoreInfo = [ordered]@{ path = $CoreRoot; found = $CoreOk; git_head = $null }

$Summary = [ordered]@{
  suite    = 'cloview-examples-windows-verification'
  stage    = '2-2b'
  started  = (Get-Date).ToString('o')
  repo     = $RepoRoot
  cloview_core = $CoreInfo
  warnings = @()
  steps    = [ordered]@{}
}

if (-not $CoreOk) {
  $w = 'cloview-core was not found at ' + $CoreRoot + '. Clone it next to this repository ' +
       '(git clone https://github.com/echojustus/cloview-core ..\cloview-core); steps 02-04 need it.'
  $Summary.warnings += $w
  Write-Host ('WARNING: ' + $w) -ForegroundColor Yellow
}

if ($RepoRoot.StartsWith('\\')) {
  $w = 'The repository is on a network or WSL path (' + $RepoRoot + '). cmd-based tools such as flutter.bat ' +
       'cannot run there; copy both repositories to a local drive (e.g. C:\src\cloview-examples next to ' +
       'C:\src\cloview-core) and run this script again.'
  $Summary.warnings += $w
  Write-Host ('WARNING: ' + $w) -ForegroundColor Yellow
}

# ---------------------------------------------------------------------------
# Helpers

# The file helpers never throw: a file briefly locked by an antivirus or a sync client
# costs a retry (or one lost line), never the rest of a step.
$LogWriteFailures = 0
function Write-Utf8File([string]$Path, [string]$Text) {
  try { [System.IO.File]::WriteAllText($Path, $Text, $Utf8NoBom) }
  catch {
    Start-Sleep -Milliseconds 200
    try { [System.IO.File]::WriteAllText($Path, $Text, $Utf8NoBom) }
    catch { Write-Host ('WARNING: could not write ' + $Path + ': ' + $_.Exception.Message) -ForegroundColor Yellow }
  }
}

function Add-Log([string]$Log, [string]$Text) {
  try { [System.IO.File]::AppendAllText($Log, $Text + [Environment]::NewLine, $Utf8NoBom) }
  catch {
    $err = $_
    $script:LogWriteFailures++
    if ($script:LogWriteFailures -le 20) {
      Start-Sleep -Milliseconds 50
      try { [System.IO.File]::AppendAllText($Log, $Text + [Environment]::NewLine, $Utf8NoBom); return } catch { }
    }
    if ($script:LogWriteFailures -le 5) {
      Write-Host ('WARNING: could not append to ' + $Log + ': ' + $err.Exception.Message) -ForegroundColor Yellow
    }
  }
}

function Write-Section([string]$Log, [string]$Title) {
  $line = '=' * 78
  Add-Log $Log ''
  Add-Log $Log $line
  Add-Log $Log ('== ' + $Title + '   [' + (Get-Date).ToString('HH:mm:ss') + ']')
  Add-Log $Log $line
  Write-Host ''
  Write-Host ('>> ' + $Title) -ForegroundColor Cyan
}

function Test-Tool([string]$Name) {
  return ($null -ne (Get-Command $Name -ErrorAction SilentlyContinue))
}

# Run a native command (or PowerShell function such as the ClojureTools `clj`),
# append stdout+stderr to $Log, return the exit code. Arguments are passed as an
# array of strings, so '-M:test' is never parsed as a PowerShell parameter.
function Invoke-Logged([string]$Log, [string]$Exe, [string[]]$Arguments, [string]$WorkDir = $RepoRoot) {
  Add-Log $Log ('$ ' + $Exe + ' ' + ($Arguments -join ' ') + '    (in ' + $WorkDir + ')')
  Push-Location -LiteralPath $WorkDir
  $global:LASTEXITCODE = 0
  $code = 0
  try {
    & $Exe @Arguments 2>&1 | ForEach-Object {
      $s = "$_"
      Add-Log $Log $s
      Write-Host $s
    }
    $code = $global:LASTEXITCODE
    if ($null -eq $code) { $code = 0 }
  } catch {
    Add-Log $Log ('!! exception: ' + $_.Exception.Message)
    $code = -1
  } finally {
    Pop-Location
  }
  Add-Log $Log ('-> exit code ' + $code)
  return [int]$code
}

function Set-Step([string]$Id, [string]$Status, [System.Collections.IDictionary]$Extra) {
  $entry = [ordered]@{ status = $Status }
  if ($Extra) { foreach ($k in $Extra.Keys) { $entry[$k] = $Extra[$k] } }
  $Summary.steps[$Id] = $entry
  $color = 'Green'
  if ($Status -eq 'failed') { $color = 'Red' } elseif ($Status -ne 'ok') { $color = 'Yellow' }
  Write-Host ('[' + $Id + '] ' + $Status) -ForegroundColor $color
}

# An unexpected exception inside a step: the steps it interrupted (those of $Ids without a
# result yet, else the last one) are marked failed with the message; the run goes on.
function Register-StepException([string[]]$Ids, [string]$Log, $Err) {
  $msg = "$($Err.Exception.Message)"
  Add-Log $Log ('!! unexpected exception: ' + $msg)
  Add-Log $Log ("$($Err.InvocationInfo.PositionMessage)")
  $open = @($Ids | Where-Object { -not $Summary.steps.Contains($_) })
  if ($open.Count -eq 0) { $open = @($Ids[-1]) }
  foreach ($id in $open) {
    if (-not $Summary.steps.Contains($id)) { $Summary.steps[$id] = [ordered]@{} }
    $Summary.steps[$id]['status'] = 'failed'
    $Summary.steps[$id]['exception'] = $msg
  }
  Write-Host ('[' + ($open -join ', ') + '] failed with an unexpected exception: ' + $msg) -ForegroundColor Red
}

function Get-ToolInfo([string]$Name, [string[]]$VersionArgs) {
  $info = [ordered]@{ present = $false }
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  if ($null -eq $cmd) { return $info }
  $info.present = $true
  $info.kind = "$($cmd.CommandType)"
  $info.source = "$($cmd.Source)"
  try {
    $global:LASTEXITCODE = 0
    $out = & $Name @VersionArgs 2>&1 | ForEach-Object { "$_" }
    $info.exit_code = $global:LASTEXITCODE
    $info.output = (($out | Select-Object -First 25) -join "`n").Trim()
  } catch {
    $info.error = $_.Exception.Message
  }
  return $info
}

function Get-DotnetMajors {
  $sdkMajors = @()
  $desktopMajors = @()
  if (Test-Tool 'dotnet') {
    foreach ($l in (& dotnet --list-sdks 2>$null)) {
      if ("$l" -match '^(\d+)\.') { $sdkMajors += [int]$Matches[1] }
    }
    foreach ($l in (& dotnet --list-runtimes 2>$null)) {
      if ("$l" -match '^Microsoft\.WindowsDesktop\.App (\d+)\.') { $desktopMajors += [int]$Matches[1] }
    }
  }
  return @{ sdk = $sdkMajors; desktop = $desktopMajors }
}

# Highest of net10/net9/net8-windows that the newest installed SDK can build (SDK >= N)
# and that some installed Microsoft.WindowsDesktop.App runtime can run: major >= N,
# because CloViewHost.csproj sets RollForward=Major (a net10 build runs on runtime 11).
function Select-ClrTfm {
  if ($ClrTargetFramework -ne '') { return $ClrTargetFramework }
  $m = Get-DotnetMajors
  if ($m.sdk.Count -eq 0 -or $m.desktop.Count -eq 0) { return $null }
  $sdkMax = ($m.sdk | Measure-Object -Maximum).Maximum
  $desktopMax = ($m.desktop | Measure-Object -Maximum).Maximum
  foreach ($n in @(10, 9, 8)) {
    if (($sdkMax -ge $n) -and ($desktopMax -ge $n)) { return ('net' + $n + '.0-windows') }
  }
  return $null
}

function Copy-SourceTree([string]$FromDir, [string]$ToDir, [string[]]$Extensions) {
  $count = 0
  if (-not (Test-Path -LiteralPath $FromDir)) { return 0 }
  $base = (Resolve-Path -LiteralPath $FromDir).ProviderPath
  Get-ChildItem -LiteralPath $base -Recurse -File | Where-Object { $Extensions -contains $_.Extension } | ForEach-Object {
    $rel = $_.FullName.Substring($base.Length).TrimStart('\', '/')
    $dest = Join-Path $ToDir $rel
    $destDir = Split-Path -Parent $dest
    if (-not (Test-Path -LiteralPath $destDir)) { New-Item -ItemType Directory -Force -Path $destDir | Out-Null }
    Copy-Item -LiteralPath $_.FullName -Destination $dest -Force
    $count++
  }
  return $count
}

function Get-Clj {
  if (Test-Tool 'clj') { return 'clj' }
  if (Test-Tool 'clojure') { return 'clojure' }
  return $null
}

# The legacy ClojureTools installer (win-install.ps1) puts the module in a folder of the
# PSModulePath of the shell that ran it. Installed from PowerShell 7 it often lands in
# Documents\PowerShell\Modules, which Windows PowerShell 5.1 does not search.
function Find-ClojureToolsModule {
  $roots = @()
  $docs = [Environment]::GetFolderPath('MyDocuments')
  if ($docs) { $roots += (Join-Path $docs 'PowerShell\Modules'); $roots += (Join-Path $docs 'WindowsPowerShell\Modules') }
  if ($env:ProgramFiles) {
    $roots += (Join-Path $env:ProgramFiles 'PowerShell\Modules')
    $roots += (Join-Path $env:ProgramFiles 'PowerShell\7\Modules')
    $roots += (Join-Path $env:ProgramFiles 'WindowsPowerShell\Modules')
  }
  foreach ($r in $roots) {
    $p = Join-Path $r 'ClojureTools\ClojureTools.psd1'
    if (Test-Path -LiteralPath $p) { return $p }
  }
  return $null
}

# Flutter version as [int[]] (major, minor, patch), or $null.
function Get-FlutterVersion {
  if (-not (Test-Tool 'flutter')) { return $null }
  try {
    $out = (& flutter --version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    if ($out -match 'Flutter (\d+)\.(\d+)\.(\d+)') {
      return @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
    }
  } catch { }
  return $null
}

# ClojureDart 0.9.20261002 (cljd.flutter) needs Flutter >= 3.35.0.
$MinFlutter = @(3, 35, 0)
function Test-FlutterVersionOk($v) {
  if ($null -eq $v) { return $false }
  for ($i = 0; $i -lt 3; $i++) {
    if ($v[$i] -gt $MinFlutter[$i]) { return $true }
    if ($v[$i] -lt $MinFlutter[$i]) { return $false }
  }
  return $true
}

# Flutter creates symlinks for plugins with Windows/Linux implementations (shared_preferences,
# path_provider): that needs Windows Developer Mode, or an elevated shell. $true/$false.
function Test-SymlinkSupport {
  try {
    $k = Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\AppModelUnlock' -ErrorAction Stop
    if ($k.AllowDevelopmentWithoutDevLicense -eq 1) { return $true }
  } catch { }
  try {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
  } catch { return $false }
}

# A Python 3.7+ that really runs: 'py -3', 'python', 'python3' (skips the Microsoft Store
# alias, which exists on PATH but only opens the Store). Returns @{ exe; args } or $null.
function Find-Python {
  foreach ($cand in @(@{ exe = 'py'; args = @('-3') }, @{ exe = 'python'; args = @() }, @{ exe = 'python3'; args = @() })) {
    if (Test-Tool $cand.exe) {
      try {
        $global:LASTEXITCODE = 0
        $v = & $cand.exe @($cand.args + @('--version')) 2>&1 | ForEach-Object { "$_" }
        # the baseline's checkers need Python 3.7+ (date.fromisoformat)
        if ($global:LASTEXITCODE -eq 0 -and ("$v" -match 'Python 3\.(\d+)') -and ([int]$Matches[1] -ge 7)) { return $cand }
      } catch { }
    }
  }
  return $null
}

$Clj = Get-Clj
$Tfm = Select-ClrTfm
$PsExe = (Get-Process -Id $PID).Path

# The official Windows Clojure CLI is a PowerShell module (ClojureTools): `clj` is an
# alias of the function Invoke-Clojure, not an exe. Two consequences handled here:
#  * Invoke-Clojure only recognises an alias argument split the way PowerShell splits a
#    typed `-M:test`, i.e. two arguments '-M:' 'test' (same for -X: -T: -A:); an unsplit
#    '-M:test' would be handed to clojure.main as a REPL argument;
#  * Invoke-Clojure sets $ErrorActionPreference='Stop', so under Windows PowerShell 5.1 a
#    2>&1 around it turns Java's first stderr line into a terminating error.
# A function/script `clj` is therefore run in a child PowerShell (its stderr is captured
# there as a plain native stream), with the alias arguments pre-split.
$CljKind = 'none'
$CljPrelude = ''
$CljNeedsProfile = $false
if ($null -eq $Clj) {
  $ctModule = Find-ClojureToolsModule
  if ($null -ne $ctModule) {
    $Clj = 'Invoke-Clojure'
    $CljKind = 'function'
    $CljPrelude = "Import-Module '" + ($ctModule -replace "'", "''") + "'; Invoke-Clojure"
    $Summary.clj_module_outside_psmodulepath = $ctModule
  }
} else {
  $ci = Get-Command $Clj -ErrorAction SilentlyContinue
  if ($null -ne $ci -and "$($ci.CommandType)" -eq 'Alias') { $ci = $ci.ResolvedCommand }
  $ct = "$($ci.CommandType)"
  if ($ct -eq 'Function') {
    $CljKind = 'function'
    $CljPrelude = $ci.Name
    if ($null -ne $ci.Module) {
      $modFile = Join-Path $ci.Module.ModuleBase ($ci.Module.Name + '.psd1')
      if (-not (Test-Path -LiteralPath $modFile)) { $modFile = $ci.Module.Path }
      $CljPrelude = "Import-Module '" + ($modFile -replace "'", "''") + "'; " + $ci.Name
    } else {
      $CljNeedsProfile = $true   # defined in a profile script
    }
  } elseif ($ct -eq 'ExternalScript') {
    $CljKind = 'script'
    $CljPrelude = "& '" + ($ci.Path -replace "'", "''") + "'"
  } else {
    $CljKind = 'app'
  }
}
$CljIsFunction = ($CljKind -eq 'function' -or $CljKind -eq 'script')

function ConvertTo-CljArgs([string[]]$Arguments) {
  $out = New-Object System.Collections.Generic.List[string]
  foreach ($a in $Arguments) {
    if ($CljIsFunction -and ($a -match '^(-[MXTA]:)(.+)$')) {
      $out.Add($Matches[1])
      $out.Add($Matches[2])
    } else {
      $out.Add($a)
    }
  }
  return ,($out.ToArray())
}

function Invoke-Clj([string]$Log, [string[]]$Arguments, [string]$WorkDir = $RepoRoot) {
  $cljArgs = ConvertTo-CljArgs $Arguments
  if (-not $CljIsFunction) { return (Invoke-Logged $Log $Clj $cljArgs $WorkDir) }
  $quoted = @()
  foreach ($a in $cljArgs) { $quoted += ("'" + ($a -replace "'", "''") + "'") }
  $cd = 'Set-Location -LiteralPath ''' + ($WorkDir -replace "'", "''") + '''; '
  $utf8 = 'try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }; '
  $command = '$ProgressPreference = ''SilentlyContinue''; ' + $utf8 + $cd + $CljPrelude + ' ' + ($quoted -join ' ') + '; exit $LASTEXITCODE'
  Add-Log $Log ('# clj is a PowerShell ' + $CljKind + '; running in a child PowerShell: ' + $command)
  $encoded = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($command))
  $psArgs = @('-NonInteractive', '-ExecutionPolicy', 'Bypass', '-OutputFormat', 'Text', '-EncodedCommand', $encoded)
  if (-not $CljNeedsProfile) { $psArgs = @('-NoProfile') + $psArgs }
  return (Invoke-Logged $Log $PsExe $psArgs $WorkDir)
}

# Copy a Flutter example to target\flutter-<name> (absolute :local/root paths), run
# `clj -M:cljd init` once (flutter create), then add the Dart packages the effect runners
# use. ClojureDart's own pubspec "bridge" (`flutter pub add name:{"path":...}`) loses its
# JSON quotes through flutter.bat, so cloview declares no bridges and adds packages here.
# $FlutterVer: when it differs from the version that built the folder last time, the
# ClojureDart build output is cleaned (`clj -M:cljd clean`) before compiling again.
$DartPackages = @('http', 'shared_preferences', 'path_provider')
function Initialize-FlutterProject([string]$Log, [string]$ExampleRel, [string]$Dir, [string[]]$ExtraTestRels, [string]$FlutterVer) {
  $r = [ordered]@{}
  $example = Join-Path $RepoRoot $ExampleRel
  New-Item -ItemType Directory -Force -Path $Dir | Out-Null
  $sourceExts = @('.clj', '.cljc', '.cljd', '.edn', '.dart')
  foreach ($d in @('src', 'test')) {
    $dest = Join-Path $Dir $d
    if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $dest) { Add-Log $Log ('warning: could not delete ' + $dest + ' completely (file in use?); copying over it') }
    # file by file, so an existing folder is never nested inside itself
    $n = Copy-SourceTree (Join-Path $example $d) $dest $sourceExts
    Add-Log $Log ('copied ' + $n + ' files of ' + $ExampleRel + '\' + $d)
  }
  foreach ($t in $ExtraTestRels) {
    $n = Copy-SourceTree (Join-Path $RepoRoot $t) (Join-Path $Dir 'test') $sourceExts
    Add-Log $Log ('copied ' + $n + ' files of ' + $t + ' into ' + (Join-Path $Dir 'test'))
  }
  $depsText = [System.IO.File]::ReadAllText((Join-Path $example 'deps.edn'))
  $depsText = [regex]::Replace($depsText, ':local/root\s+"([^"]+)"', {
      param($m)
      $abs = [System.IO.Path]::GetFullPath((Join-Path $example $m.Groups[1].Value))
      ':local/root "' + ($abs -replace '\\', '/') + '"'
    })
  Write-Utf8File (Join-Path $Dir 'deps.edn') $depsText
  Add-Log $Log $depsText

  $pubspec = Join-Path $Dir 'pubspec.yaml'
  if (-not (Test-Path -LiteralPath $pubspec)) {
    Write-Section $Log ('clj -M:cljd init (flutter create) in ' + $Dir)
    $r.init_exit = Invoke-Clj $Log @('-M:cljd', 'init', '--platforms=windows,android,linux,macos') $Dir
    # cljd's init exits 0 even when flutter create failed: the pubspec is the real signal
    if (($r.init_exit -eq 0) -and -not (Test-Path -LiteralPath $pubspec)) { $r.init_exit = 'no-pubspec' }
    $sample = Join-Path $Dir 'test\widget_test.dart'
    if (Test-Path -LiteralPath $sample) { Remove-Item -LiteralPath $sample -Force; Add-Log $Log 'removed flutter create sample test\widget_test.dart' }
  } else {
    $r.init_exit = 0
    Add-Log $Log 'pubspec.yaml exists - init skipped'
  }

  # a Flutter upgrade invalidates ClojureDart's output built with the previous SDK
  $stamp = Join-Path $Dir '.cloview-flutter-version'
  $prev = $null
  if (Test-Path -LiteralPath $stamp) { try { $prev = ([System.IO.File]::ReadAllText($stamp)).Trim() } catch { } }
  if ((Test-Path -LiteralPath (Join-Path $Dir '.clojuredart')) -and ($prev -ne $FlutterVer)) {
    Write-Section $Log ('clj -M:cljd clean  (built with Flutter ' + $(if ($prev) { $prev } else { 'unknown' }) + ', now ' + $FlutterVer + ')')
    $r.clean_exit = Invoke-Clj $Log @('-M:cljd', 'clean') $Dir
  }
  if ($FlutterVer) { Write-Utf8File $stamp $FlutterVer }

  if (Test-Path -LiteralPath $pubspec) {
    $missingPkgs = {
      $t = ''
      try { $t = [System.IO.File]::ReadAllText($pubspec) } catch { }   # unreadable: all missing (pub add is idempotent)
      @($DartPackages | Where-Object { -not ($t -match ('(?m)^\s+' + [regex]::Escape($_) + ':')) })
    }
    $missing = @(& $missingPkgs)
    if ($missing.Count -gt 0) {
      Write-Section $Log ('flutter pub add ' + ($missing -join ' '))
      $r.pub_add_exit = Invoke-Logged $Log 'flutter' (@('pub', 'add') + $missing) $Dir
      if (($r.pub_add_exit -ne 0) -and (@(& $missingPkgs).Count -eq 0)) {
        # The packages were added and resolved; what failed is Flutter's plugin symlink step
        # (shared_preferences / path_provider have Windows implementations). Compiling and
        # `flutter test` work without it; running the Windows app needs Developer Mode.
        Add-Log $Log ('pub add: packages are in pubspec.yaml, but Flutter could not finish (exit ' + $r.pub_add_exit +
                      '). Usually this is "Building with plugins requires symlink support": enable Windows Developer Mode ' +
                      '(start ms-settings:developers). Continuing: compile and tests do not need the symlinks.')
        $r.pub_add_warning = 'plugin-symlinks'
        $r.pub_add_exit = 0
      }
    } else {
      $r.pub_add_exit = 0
      Add-Log $Log ('Dart packages already present: ' + ($DartPackages -join ' '))
    }
  } else {
    $r.pub_add_exit = 'no-pubspec'
  }
  return $r
}

# Text appended to $Log since $Before (its earlier content), to look for a compile error.
function Get-LogTail([string]$Log, [string]$Before) {
  $now = Read-Log $Log
  return $now.Substring([Math]::Min($Before.Length, $now.Length))
}
function Read-Log([string]$Log) {
  try { if (Test-Path -LiteralPath $Log) { return [System.IO.File]::ReadAllText($Log) } } catch { }
  return ''
}

# `clj -M:cljd-diag compile`: the same compile, printing the full cause chain of an error
# (cljd's own message names only the outermost namespace).
function Invoke-CljdDiagnostics([string]$Log, [string]$Dir) {
  Write-Section $Log 'Compile error diagnostics: clj -M:cljd-diag compile --offline (full cause chain)'
  return (Invoke-Clj $Log @('-M:cljd-diag', 'compile', '--offline') $Dir)
}

# The commit a checkout is at (git rev-parse HEAD), or $null without git or outside a clone.
function Get-GitHead([string]$Root) {
  if (-not (Test-Tool 'git')) { return $null }
  try {
    $global:LASTEXITCODE = 0
    $out = & git -C $Root rev-parse HEAD 2>$null
    if ($global:LASTEXITCODE -eq 0 -and $out) { return ("$out").Trim() }
  } catch { }
  return $null
}

# Envelopes (FITNESS_BUSINESS_RESULTS_V1) the host steps produced or should have produced,
# for the --observed checks of step 05.
$Envelopes = @()

# ---------------------------------------------------------------------------
# 01 - environment (+ the cloview-core checkout)

try {   # (each step: try { ... } catch { Register-StepException ... }, see above)
$envStart = Get-Date
$os = [ordered]@{
  version_string = [System.Environment]::OSVersion.VersionString
  is_64bit = [System.Environment]::Is64BitOperatingSystem
  culture = (Get-Culture).Name
  ui_culture = (Get-UICulture).Name
  oem_code_page = [System.Globalization.CultureInfo]::CurrentCulture.TextInfo.OEMCodePage
}
try {
  $w = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
  $os.caption = $w.Caption
  $os.build = $w.BuildNumber
} catch { $os.caption = 'unknown' }

$CoreInfo.git_head = $(if ($CoreOk) { Get-GitHead $CoreRoot } else { $null })
$dotnetMajors = Get-DotnetMajors
$FlutterVersion = Get-FlutterVersion
$Python = Find-Python
$SymlinkOk = Test-SymlinkSupport
$envReport = [ordered]@{
  generated = (Get-Date).ToString('o')
  os = $os
  powershell = "$($PSVersionTable.PSVersion)"
  repo = $RepoRoot
  repo_head = (Get-GitHead $RepoRoot)
  cloview_core = $CoreInfo
  tools = [ordered]@{
    java = Get-ToolInfo 'java' @('-version')
    clj = Get-ToolInfo 'clj' @('-Sdescribe')
    clojure = Get-ToolInfo 'clojure' @('--version')
    bb = Get-ToolInfo 'bb' @('--version')
    git = Get-ToolInfo 'git' @('--version')
    dotnet = Get-ToolInfo 'dotnet' @('--info')
    flutter = Get-ToolInfo 'flutter' @('--version')
    dart = Get-ToolInfo 'dart' @('--version')
  }
  flutter = [ordered]@{
    version = $(if ($null -ne $FlutterVersion) { $FlutterVersion -join '.' } else { $null })
    minimum = ($MinFlutter -join '.')
    ok = (Test-FlutterVersionOk $FlutterVersion)
    symlink_support = $SymlinkOk
  }
  python = $(if ($null -ne $Python) { ($Python.exe + ' ' + ($Python.args -join ' ')).Trim() } else { $null })
  dotnet = [ordered]@{
    sdk_majors = $dotnetMajors.sdk
    windows_desktop_runtime_majors = $dotnetMajors.desktop
    selected_clr_tfm = $Tfm
  }
  java_tool_options = $env:JAVA_TOOL_OPTIONS
}
Write-Utf8File (Join-Path $OutDir '01-env-check.json') ($envReport | ConvertTo-Json -Depth 6)
$s1 = 'ok'
if (-not $CoreOk) { $s1 = 'failed' }
Set-Step '01-env-check' $s1 @{ duration_s = [int]((Get-Date) - $envStart).TotalSeconds; clj = $Clj; clj_kind = $CljKind; clr_tfm = $Tfm
                               flutter = $envReport.flutter.version; flutter_ok = $envReport.flutter.ok; python = $envReport.python
                               cloview_core_found = $CoreOk; cloview_core_head = $CoreInfo.git_head }
if (($null -ne $FlutterVersion) -and -not (Test-FlutterVersionOk $FlutterVersion)) {
  $w = 'Flutter ' + ($FlutterVersion -join '.') + ' is older than 3.35, the minimum of ClojureDart 0.9.20261002 ' +
       '(cljd.flutter uses AlignmentGeometry.topLeft). Run `flutter upgrade` (3.47.x stable recommended) or pass -FlutterUpgrade.'
  $Summary.warnings += $w
  Write-Host ('WARNING: ' + $w) -ForegroundColor Yellow
}
if ((Test-Tool 'flutter') -and -not $SkipFlutter -and -not $SymlinkOk) {
  $w = 'Windows Developer Mode is off and this shell is not elevated. Flutter needs symlink support for ' +
       'plugins (shared_preferences, path_provider): `flutter pub add` will report "Building with plugins requires ' +
       'symlink support". Compile and tests still run; running the Flutter Windows app needs Developer Mode ' +
       '(open it with: start ms-settings:developers).'
  $Summary.warnings += $w
  Write-Host ('WARNING: ' + $w) -ForegroundColor Yellow
}
} catch { Register-StepException @('01-env-check') (Join-Path $OutDir '01-env-check.log') $_ }

# ---------------------------------------------------------------------------
# 02 - JVM: fitness unit tests + DOM-001 oracles, static check, JavaFX smoke

$log02 = Join-Path $OutDir '02-jvm.log'
try {
$start = Get-Date
if ($SkipJvm) {
  Add-Log $log02 'skipped (-SkipJvm)'
  Set-Step '02-jvm' 'skipped' @{ reason = '-SkipJvm' }
} elseif ($null -eq $Clj) {
  Add-Log $log02 'skipped: neither clj nor clojure is on PATH (install the Clojure CLI for Windows)'
  Set-Step '02-jvm' 'skipped' @{ reason = 'clj not found' }
} elseif (-not $CoreOk) {
  Add-Log $log02 ('not run: cloview-core not found at ' + $CoreRoot)
  Set-Step '02-jvm' 'failed' @{ reason = 'cloview-core not found' }
} else {
  $jvm = [ordered]@{}
  Write-Section $log02 'JVM: fitness unit tests + DOM-001 business oracles (clj -M:fitness)'
  $jvm.tests_exit = Invoke-Clj $log02 @('-M:fitness', '--out-dir', 'verify/out', '--prefix', '02-fitness-')
  $script:Envelopes += '02-fitness-jvm-business-results.json'
  Write-Section $log02 'Static check of apps\ against cloview-core: calls/arities, portability, ClojureDart coverage'
  $jvm.static_check_exit = Invoke-Clj $log02 @('-M:static-check')
  Write-Section $log02 'JavaFX off-screen smoke of the fitness app via cljfx 1.10.363 from Clojars'
  $json02 = 'verify/out/02-fitness-jvm-smoke.json'
  $jvm.cljfx_from_git = $false
  $jvm.smoke_exit = Invoke-Clj $log02 @('-M:smoke-fitness-javafx', $json02)
  if ($jvm.smoke_exit -ne 0 -and -not (Test-Path -LiteralPath (Join-Path $RepoRoot $json02))) {
    Write-Section $log02 'Retry with cljfx from GitHub (:cljfx-git alias)'
    $jvm.smoke_exit = Invoke-Clj $log02 @('-M:smoke-fitness-javafx:cljfx-git', $json02)
    $jvm.cljfx_from_git = $true
  }
  $s2 = 'ok'
  if ($jvm.tests_exit -ne 0 -or $jvm.static_check_exit -ne 0 -or $jvm.smoke_exit -ne 0) { $s2 = 'failed' }
  $jvm.duration_s = [int]((Get-Date) - $start).TotalSeconds
  Set-Step '02-jvm' $s2 $jvm
}
} catch { Register-StepException @('02-jvm') $log02 $_ }

# ---------------------------------------------------------------------------
# 03 - CLR: staging, CloViewHost build, fitness unit tests + oracles, WPF smoke

$log03 = Join-Path $OutDir '03-clr.log'
$start = Get-Date
$stagedDir = Join-Path $RepoRoot 'target\clr-src'
$hostProject = Join-Path $RepoRoot 'target\clr-host-project'
$hostOut = Join-Path $RepoRoot 'target\clr-host'
$HostExe = Join-Path $hostOut 'CloViewHost.exe'
try {
if ($SkipClr) {
  Add-Log $log03 'skipped (-SkipClr)'
  Set-Step '03-clr' 'skipped' @{ reason = '-SkipClr' }
} elseif (-not $CoreOk) {
  Add-Log $log03 ('not run: cloview-core not found at ' + $CoreRoot)
  Set-Step '03-clr' 'failed' @{ reason = 'cloview-core not found' }
} else {
  $clr = [ordered]@{}

  Write-Section $log03 'Stage the .clj/.cljc/.cljr sources of cloview-core and the apps into target\clr-src'
  if (Test-Path -LiteralPath $stagedDir) { Remove-Item -LiteralPath $stagedDir -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $stagedDir | Out-Null
  # keep in sync with clr-source-dirs in verify/bb/cloview/examples/tasks.clj
  $coreDirs = @('modules\core\src', 'modules\fx\src', 'modules\bridge\src', 'modules\licensing\src', 'modules\wpf\src', 'verify\src')
  $appDirs = @('apps\fitness\shared\src', 'apps\fitness\shared\test', 'apps\fitness\wpf\src')
  $staged = 0
  foreach ($d in $coreDirs) {
    $n = Copy-SourceTree (Join-Path $CoreRoot $d) $stagedDir @('.clj', '.cljc', '.cljr')
    Add-Log $log03 ('  cloview-core\' + $d + ' -> ' + $n + ' files')
    $staged += $n
  }
  foreach ($d in $appDirs) {
    $n = Copy-SourceTree (Join-Path $RepoRoot $d) $stagedDir @('.clj', '.cljc', '.cljr')
    Add-Log $log03 ('  ' + $d + ' -> ' + $n + ' files')
    $staged += $n
  }
  Add-Log $log03 ('staged ' + $staged + ' files into ' + $stagedDir)
  $clr.staged_files = $staged

  Write-Section $log03 ('dotnet build CloViewHost.csproj, copied from cloview-core (TargetFramework ' + $Tfm + ')')
  if (-not (Test-Tool 'dotnet')) {
    Add-Log $log03 'dotnet not on PATH: install the .NET 10 SDK (or 8/9)'
    $clr.build_exit = 'no-dotnet'
  } elseif ($null -eq $Tfm) {
    Add-Log $log03 'no usable .NET SDK (>= 8) + Microsoft.WindowsDesktop.App runtime (>= 8) found - install the .NET 10 SDK'
    $clr.build_exit = 'no-tfm'
  } else {
    # built from a copy of the template under target\: the cloview-core checkout is never written to
    New-Item -ItemType Directory -Force -Path $hostProject | Out-Null
    foreach ($f in @('CloViewHost.csproj', 'Program.cs')) {
      Copy-Item -LiteralPath (Join-Path $CoreRoot ('modules\wpf\host-template\' + $f)) -Destination (Join-Path $hostProject $f) -Force
    }
    if (Test-Path -LiteralPath $hostOut) { Remove-Item -LiteralPath $hostOut -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $HostExe) { Add-Log $log03 ('warning: could not delete ' + $hostOut + ' - is CloViewHost.exe still running?') }
    $clr.tfm = $Tfm
    $clr.build_exit = Invoke-Logged $log03 'dotnet' @('build', (Join-Path $hostProject 'CloViewHost.csproj'), '-c', 'Release',
                                                      ('-p:CloViewTargetFramework=' + $Tfm), ('-p:CloViewSrcDir=' + $stagedDir),
                                                      '-o', $hostOut)
  }

  if (($clr.build_exit -is [int]) -and ($clr.build_exit -eq 0) -and (Test-Path -LiteralPath $HostExe)) {
    Write-Section $log03 'CLR: fitness unit tests + DOM-001 oracles (CloViewHost --ns fitness.verify --run-tests)'
    $clr.tests_exit = Invoke-Logged $log03 $HostExe @('--src', $stagedDir, '--ns', 'fitness.verify', '--run-tests',
                                                      '--out', (Join-Path $OutDir '03-fitness-clr-summary.json'))
    $script:Envelopes += '03-fitness-clr-business-results.json'
    Write-Section $log03 'CLR: off-screen WPF smoke of the fitness app (CloViewHost --ns fitness.wpf.main --headless-smoke)'
    $clr.smoke_exit = Invoke-Logged $log03 $HostExe @('--src', $stagedDir, '--ns', 'fitness.wpf.main', '--headless-smoke',
                                                      '--out', (Join-Path $OutDir '03-fitness-clr-wpf-smoke.json'))
  } else {
    Add-Log $log03 ('CloViewHost was not built in this run (' + $HostExe + ') - skipping CLR runs')
    $clr.tests_exit = 'not-run'
    $clr.smoke_exit = 'not-run'
  }

  $s3 = 'ok'
  if (-not ($clr.build_exit -is [int]) -or $clr.build_exit -ne 0 -or $clr.tests_exit -ne 0 -or $clr.smoke_exit -ne 0) { $s3 = 'failed' }
  $clr.duration_s = [int]((Get-Date) - $start).TotalSeconds
  Set-Step '03-clr' $s3 $clr
}
} catch { Register-StepException @('03-clr') $log03 $_ }

# ---------------------------------------------------------------------------
# 04 - Dart: Flutter version check, ClojureDart compile, widget test + oracles

$log04 = Join-Path $OutDir '04-dart.log'
$start = Get-Date
try {
if ($SkipFlutter) {
  Add-Log $log04 'skipped (-SkipFlutter)'
  Set-Step '04-dart' 'skipped' @{ reason = '-SkipFlutter' }
} elseif (-not (Test-Tool 'flutter')) {
  Add-Log $log04 'skipped: flutter is not on PATH'
  Set-Step '04-dart' 'skipped' @{ reason = 'flutter not found' }
} elseif ($null -eq $Clj) {
  Add-Log $log04 'skipped: clj is not on PATH (ClojureDart runs on the Clojure CLI)'
  Set-Step '04-dart' 'skipped' @{ reason = 'clj not found' }
} elseif (-not $CoreOk) {
  Add-Log $log04 ('not run: cloview-core not found at ' + $CoreRoot)
  Set-Step '04-dart' 'failed' @{ reason = 'cloview-core not found' }
} else {
  $dart = [ordered]@{}
  Write-Section $log04 'flutter --version'
  $dart.flutter_version_exit = Invoke-Logged $log04 'flutter' @('--version')
  $v = Get-FlutterVersion
  if (-not (Test-FlutterVersionOk $v) -and $FlutterUpgrade) {
    Write-Section $log04 'flutter upgrade  (-FlutterUpgrade: the installed Flutter is older than 3.35)'
    $dart.flutter_upgrade_exit = Invoke-Logged $log04 'flutter' @('upgrade')
    $v = Get-FlutterVersion
  }
  $dart.flutter_version = $(if ($null -ne $v) { $v -join '.' } else { 'unknown' })
  if ($null -eq $v) {
    Add-Log $log04 'Could not read the Flutter version from `flutter --version` (see its output above). Compile and tests skipped.'
    $dart.reason = 'flutter-version-unparsed'
    $dart.duration_s = [int]((Get-Date) - $start).TotalSeconds
    Set-Step '04-dart' 'failed' $dart
  } elseif (-not (Test-FlutterVersionOk $v)) {
    Add-Log $log04 ('Flutter ' + $dart.flutter_version + ' is too old: ClojureDart 0.9.20261002 needs Flutter >= 3.35.0 ' +
                    '(cljd.flutter uses AlignmentGeometry.topLeft, added in Flutter 3.35). Every ClojureDart Flutter app ' +
                    'fails to compile on older versions. Run `flutter upgrade` (3.47.x stable recommended), or run this ' +
                    'script with -FlutterUpgrade. Compile and tests skipped.')
    $dart.reason = 'flutter-too-old'
    $dart.duration_s = [int]((Get-Date) - $start).TotalSeconds
    Set-Step '04-dart' 'failed' $dart
  } else {
    # fitness.verify names the Dart envelope after this prefix (the JVM and CLR get theirs as arguments)
    $env:CLOVIEW_FITNESS_PREFIX = '04-fitness-'
    $fitnessDir = Join-Path $RepoRoot 'target\flutter-fitness'
    Write-Section $log04 'Prepare target\flutter-fitness (app + shared tests, absolute :local/root paths, Dart packages)'
    $prep = Initialize-FlutterProject $log04 'apps\fitness\flutter' $fitnessDir @('apps\fitness\shared\test') $dart.flutter_version
    foreach ($k in $prep.Keys) { $dart[$k] = $prep[$k] }

    Write-Section $log04 'clj -M:cljd compile  (fitness app: fitness.flutter.main, natives, shared views, cloview.flutter)'
    $dart.compile_exit = Invoke-Clj $log04 @('-M:cljd', 'compile') $fitnessDir
    if ($dart.compile_exit -ne 0) { $dart.diag_exit = Invoke-CljdDiagnostics $log04 $fitnessDir }

    Write-Section $log04 'clj -M:test:cljd test  (fitness widget test, shared tests, DOM-001 on Dart)'
    $beforeTests = Read-Log $log04
    $dart.tests_exit = Invoke-Clj $log04 @('-M:test:cljd', 'test') $fitnessDir
    $script:Envelopes += '04-fitness-dart-business-results.json'
    # re-run with the cause chain only when the failure was a compile error, not a test failure
    if (($dart.tests_exit -ne 0) -and ((Get-LogTail $log04 $beforeTests) -match 'Compilation error')) {
      Write-Section $log04 'diagnostic re-run  clj -M:test:cljd-diag test  (full cause chain of a compile error)'
      $dart.tests_diag_exit = Invoke-Clj $log04 @('-M:test:cljd-diag', 'test') $fitnessDir
    }

    $s4 = 'ok'
    if ($dart.init_exit -ne 0 -or $dart.pub_add_exit -ne 0 -or $dart.compile_exit -ne 0 -or $dart.tests_exit -ne 0) { $s4 = 'failed' }
    $dart.duration_s = [int]((Get-Date) - $start).TotalSeconds
    Set-Step '04-dart' $s4 $dart
  }
}
} catch { Register-StepException @('04-dart') $log04 $_ }

# ---------------------------------------------------------------------------
# 05 - the baseline's own Python tools: package integrity, fixtures, --observed

$log05 = Join-Path $OutDir '05-baseline-checks.log'
$start = Get-Date
try {
$baselineDir = Join-Path $RepoRoot 'benchmarks\fitness-baseline'
if ($null -eq $Python) { $Python = Find-Python }   # step 01 may have stopped before looking
if ($null -eq $Python) {
  Add-Log $log05 'Python 3.7+ not found (py -3 / python / python3): baseline checkers skipped; the Clojure oracle comparisons of steps 02-04 still ran'
  Set-Step '05-baseline-checks' 'skipped' @{ reason = 'python not found' }
} else {
  $base = [ordered]@{}
  $base.python = ($Python.exe + ' ' + ($Python.args -join ' ')).Trim()
  $pyIso = @('-I', '-X', 'utf8')
  Write-Section $log05 'Python: tools\check_package.py (baseline integrity)'
  $base.check_package_exit = Invoke-Logged $log05 $Python.exe ($Python.args + $pyIso + @('tools\check_package.py')) $baselineDir
  Write-Section $log05 'Python: tools\check_fixtures.py (fixture consistency)'
  $base.check_fixtures_exit = Invoke-Logged $log05 $Python.exe ($Python.args + $pyIso + @('tools\check_fixtures.py')) $baselineDir
  $observed = [ordered]@{}
  foreach ($name in $Envelopes) {
    $full = Join-Path $OutDir $name
    if (Test-Path -LiteralPath $full) {
      Write-Section $log05 ('Python: tools\check_fixtures.py --observed ' + $name)
      $observed[$name] = Invoke-Logged $log05 $Python.exe ($Python.args + $pyIso + @('tools\check_fixtures.py', '--observed', $full)) $baselineDir
    } else {
      Add-Log $log05 ('missing envelope ' + $name + ' (its host run failed before writing it)')
      $observed[$name] = 'missing'
    }
  }
  $base.observed = $observed

  # failed: a checker failed or an envelope is missing; partial: a host did not run
  $s5 = 'ok'
  foreach ($x in @($base.check_package_exit, $base.check_fixtures_exit)) { if ($x -ne 0) { $s5 = 'failed' } }
  foreach ($k in $observed.Keys) {
    $x = $observed[$k]
    if ($x -is [int]) { if ($x -ne 0) { $s5 = 'failed' } } else { $s5 = 'failed' }
  }
  $hostsRun = @($Envelopes | ForEach-Object { ($_ -split '-')[2] })
  $base.hosts_not_run = @(@('jvm', 'clr', 'dart') | Where-Object { $hostsRun -notcontains $_ })
  if (($base.hosts_not_run.Count -gt 0) -and ($s5 -eq 'ok')) { $s5 = 'partial' }
  $base.duration_s = [int]((Get-Date) - $start).TotalSeconds
  Set-Step '05-baseline-checks' $s5 $base
}
} catch { Register-StepException @('05-baseline-checks') $log05 $_ }

$RunCompleted = $true
} catch {
  # the steps catch their own exceptions: this one happened between steps
  $err = $_
  if ($null -ne $Summary) { $Summary.aborted = ('exception: ' + $err.Exception.Message) }
  Write-Host ('ERROR: ' + $err.Exception.Message) -ForegroundColor Red
  Write-Host ("$($err.InvocationInfo.PositionMessage)")
} finally {
  # restore first: it needs no cmdlets, so it also completes while Ctrl+C stops the run
  Restore-Session
  try { Complete-Run } catch { Write-Host ('ERROR: could not write the summary or the zip: ' + $_.Exception.Message) -ForegroundColor Red }
}
exit 0
