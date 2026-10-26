param(
    [string]$User = "alice",
    [string]$Session = "",
    [string]$DataDir = "",
    [string]$Model = "",
    [switch]$New,
    [string]$Title = ""
)

$ErrorActionPreference = "Stop"
# Make Chinese prompts/responses readable in Windows terminals.
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# Convenience for local Windows runs: pick up the user-level DeepSeek key if
# this PowerShell process has not been restarted since the key was configured.
if (-not $env:LLM_API_KEY -and -not $env:DEEPSEEK_API_KEY) {
    $userKey = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User")
    if ($userKey) { $env:DEEPSEEK_API_KEY = $userKey }
}
if (-not $env:LLM_API_KEY -and -not $env:DEEPSEEK_API_KEY) {
    Write-Error "Set LLM_API_KEY (or DEEPSEEK_API_KEY) before running. See .env.example."
}

$jar = Join-Path $root "target\mini-agent.jar"
if (-not (Test-Path $jar)) {
    mvn -q -DskipTests package
}

$argsList = @("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
    "-jar", $jar, "--user", $User)
if ($Session) { $argsList += @("--session", $Session) }
if ($DataDir) { $argsList += @("--data-dir", $DataDir) }
if ($Model)   { $argsList += @("--model", $Model) }
if ($New)     { $argsList += "--new" }
if ($Title)   { $argsList += @("--title", $Title) }
& java @argsList
