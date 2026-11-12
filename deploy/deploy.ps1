<#
.SYNOPSIS
Deploys serial-protocol to a Linux Docker server over one ssh connection (Windows client).

.DESCRIPTION
Builds the jar, uploads it with the Dockerfile, docker-compose.yml and cert\server.p12 over ONE ssh
connection, builds the image on the server while the running version keeps serving, then swaps the
app container (about 15-35 s of downtime) and rolls back if the new one is not healthy.
ssh asks for the password itself; it is never read, passed or stored by this script.
Values not given as parameters are asked for, with defaults from deploy\deploy.conf
(or deploy\deploy.conf.example). Runs on Windows PowerShell 5.1 and PowerShell 7. See deploy\README.md.

.PARAMETER Server
Server IP or host name.
.PARAMETER Port
ssh port (default 22).
.PARAMETER User
ssh user (needs Docker access on the server).
.PARAMETER Dir
Absolute folder on the server, e.g. /opt/serial-protocol.
.PARAMETER SkipBuild
Do not run Maven; use the existing target\serial-protocol-*.jar.
.PARAMETER UpdateCompose
Replace the server's docker-compose.yml when it differs (a diff is shown first).
.PARAMETER Backup
Dump the database before the swap even without pending migrations.
.PARAMETER Yes
Do not ask for the final confirmation.
.PARAMETER Rollback
Switch back to the previously deployed image.
.PARAMETER Status
Show the current and previous image and the containers.
.PARAMETER Attach
Follow the log of the last deploy again (e.g. after a dropped connection).
.PARAMETER Help
Show this help.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1
.EXAMPLE
powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1 -Server 192.168.2.4 -User admin -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$Server, [string]$Port, [string]$User, [string]$Dir,
    [switch]$SkipBuild, [switch]$UpdateCompose, [switch]$Backup, [switch]$Yes,
    [switch]$Rollback, [switch]$Status, [switch]$Attach, [switch]$Help
)
if ($Help) { Get-Help -Detailed $PSCommandPath; exit 0 }
Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'

function Fail([string]$msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$cfg = @{ DEPLOY_HOST = $Server; DEPLOY_PORT = $Port; DEPLOY_USER = $User; DEPLOY_DIR = $Dir }

# KEY=VALUE allowlist; the file is never executed. CLI wins, then deploy.conf, then the example.
foreach ($f in 'deploy\deploy.conf', 'deploy\deploy.conf.example') {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    foreach ($line in Get-Content -LiteralPath $f) {
        $line = ($line -replace '#.*$', '').Trim()
        if ($line -eq '') { continue }
        $k, $v = $line -split '=', 2
        $k = $k.Trim()
        if (-not $cfg.ContainsKey($k)) { Write-Warning "${f}: ignoring unknown key '$k'"; continue }
        if (-not $cfg[$k] -and $v) { $cfg[$k] = $v.Trim() }
    }
}

$bound = $PSBoundParameters
function Ask([string]$key, [string]$param, [string]$label) {
    if ($bound.ContainsKey($param)) { return }
    $ans = Read-Host "$label [$($cfg[$key])]"
    if ($ans) { $cfg[$key] = $ans.Trim() }
}
Ask DEPLOY_HOST Server 'Server IP or host'
Ask DEPLOY_PORT Port 'SSH port'
Ask DEPLOY_USER User 'SSH user'
Ask DEPLOY_DIR Dir 'Folder on the server'
if (-not $cfg.DEPLOY_PORT) { $cfg.DEPLOY_PORT = '22' }
$h = "$($cfg.DEPLOY_HOST)"; $p = "$($cfg.DEPLOY_PORT)"; $u = "$($cfg.DEPLOY_USER)"; $d = "$($cfg.DEPLOY_DIR)".TrimEnd('/')

# Everything that reaches the remote command line is validated here, so it needs no quoting.
if ($h -notmatch '^[A-Za-z0-9.:-]+$' -or $h.StartsWith('-')) { Fail "invalid host '$h'" }
if ($p -notmatch '^[0-9]{1,5}$') { Fail "invalid port '$p'" }
if ($u -notmatch '^[A-Za-z_][A-Za-z0-9_.-]*$') { Fail "invalid user '$u'" }
if ($d -cnotmatch '^/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)+$' -or $d.Contains('..')) {
    Fail "invalid folder '$d' (absolute, at least two levels, letters, digits . _ - only)"
}

$ssh = Join-Path $env:SystemRoot 'System32\OpenSSH\ssh.exe'
if (-not (Test-Path -LiteralPath $ssh)) { $ssh = 'ssh' }
$knownHosts = ((Join-Path $root 'deploy\.known_hosts') -replace '\\', '/') -replace '%', '%%'
$sshArgs = @('-p', $p,
    '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4',
    '-o', 'StrictHostKeyChecking=accept-new', '-o', "UserKnownHostsFile=`"$knownHosts`"",
    '-o', 'PubkeyAuthentication=no', '-o', 'PreferredAuthentications=password,keyboard-interactive',
    "$u@$h")

# Windows command-line quoting for ProcessStartInfo.Arguments (PS 5.1 has no ArgumentList).
function Quote([string]$a) {
    if ($a -notmatch '[\s"]') { return $a }
    '"' + (($a -replace '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
}

# ssh with stdin from a file; a PS 5.1 pipe would corrupt the binary archive.
function Invoke-Ssh([string]$remote, [string]$stdinFile) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $ssh
    $psi.Arguments = (($sshArgs + $remote) | ForEach-Object { Quote $_ }) -join ' '
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = [bool]$stdinFile
    $proc = [System.Diagnostics.Process]::Start($psi)
    if ($stdinFile) {
        $fs = [System.IO.File]::OpenRead($stdinFile)
        try { $fs.CopyTo($proc.StandardInput.BaseStream) } catch { } finally { $fs.Close() }
        try { $proc.StandardInput.Close() } catch { }
    }
    $proc.WaitForExit()
    return $proc.ExitCode
}

$action = if ($Rollback) { 'rollback' } elseif ($Status) { 'status' } elseif ($Attach) { 'attach' } else { '' }
if ($action) { exit (Invoke-Ssh "sh $d/deploy-remote.sh $action $d" $null) }

if (-not (Test-Path -LiteralPath 'cert\server.p12')) { Write-Warning "no local cert\server.p12; the server's copy will be used" }
if (-not $SkipBuild) {
    & .\mvnw.cmd -q clean package '-Dmaven.test.skip=true'
    if ($LASTEXITCODE -ne 0) { Fail 'maven build failed' }
}
$jars = @(Get-ChildItem -Path 'target' -Filter 'serial-protocol-*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Extension -eq '.jar' })
if ($jars.Count -ne 1) { Fail "expected exactly one target\serial-protocol-*.jar, found $($jars.Count)" }
$version = $jars[0].BaseName -replace '^serial-protocol-', ''
# The version goes into the remote command line unquoted, so it must be plain.
if ($version -cnotmatch '^[0-9A-Za-z._-]+$') { Fail "unsupported characters in jar version '$version'" }
$tag = "$version-" + (Get-Date).ToString('yyyyMMddHHmmss', [System.Globalization.CultureInfo]::InvariantCulture)

if (-not $Yes) {
    $ans = Read-Host "Deploy $tag to $u@${h}:${d}? [y/N]"
    if ($ans -notmatch '^[yY]') { Fail 'cancelled' }
}

$stage = Join-Path ([System.IO.Path]::GetTempPath()) "serial-protocol-deploy-$tag"
$archive = "$stage.tgz"
$rc = 1
try {
    New-Item -ItemType Directory -Force -Path "$stage\build\target", "$stage\build\cert" | Out-Null
    Copy-Item -LiteralPath $jars[0].FullName -Destination "$stage\build\target\"
    if (Test-Path -LiteralPath 'cert\server.p12') { Copy-Item -LiteralPath 'cert\server.p12' -Destination "$stage\build\cert\" }
    # Text files go up with LF endings whatever git's autocrlf did to the checkout.
    $utf8 = New-Object System.Text.UTF8Encoding $false
    foreach ($pair in @(@('Dockerfile', 'build\Dockerfile'), @('docker-compose.yml', 'docker-compose.yml'),
            @('deploy\deploy-remote.sh', 'deploy-remote.sh'))) {
        $text = [System.IO.File]::ReadAllText((Join-Path $root $pair[0])) -replace "`r", ''
        [System.IO.File]::WriteAllText((Join-Path $stage $pair[1]), $text, $utf8)
    }
    # The built-in bsdtar; a GNU tar from Git would read "C:" as a remote host. It also lists zip files.
    $tar = Join-Path $env:SystemRoot 'System32\tar.exe'
    # Migrations of the jar being deployed, not of the working tree (it may be a different commit).
    $migrations = @(& $tar -tf $jars[0].FullName | Where-Object { $_ -like 'BOOT-INF/classes/db/migration/?*' } |
        ForEach-Object { $_.Substring('BOOT-INF/classes/db/migration/'.Length) })
    if ($LASTEXITCODE -ne 0 -or $migrations.Count -eq 0) { Fail "no Flyway migrations found in $($jars[0].Name)" }
    [System.IO.File]::WriteAllText("$stage\MIGRATIONS", (($migrations -join "`n") + "`n"), $utf8)
    & $tar -czf $archive -C $stage .
    if ($LASTEXITCODE -ne 0) { Fail 'tar failed' }

    $flags = @()
    if ($UpdateCompose) { $flags += '--update-compose' }
    if ($Backup) { $flags += '--backup' }
    $inc = "$d/.incoming-$tag"
    Write-Host 'Uploading and deploying; ssh will ask for the password once.'
    $rc = Invoke-Ssh "umask 077 && mkdir -p $inc && tar xzf - -C $inc && exec sh $inc/deploy-remote.sh start $d $inc $tag $($flags -join ' ')" $archive
}
finally {
    Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
}
exit $rc
