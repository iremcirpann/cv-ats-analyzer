# CV ATS Analyzer - AI Orchestration
# First test: synthetic Java code only

$ErrorActionPreference = "Stop"
# Use UTF-8 for PowerShell and external CLI output
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$model = "evren/deepseek-v4.1-flash"
$config = "$env:USERPROFILE\.config\opencode-evren\opencode.json"

# Check prerequisites
if ([string]::IsNullOrWhiteSpace($env:EVREN_API_KEY)) {
    throw "EVREN_API_KEY is missing."
}

if (-not (Test-Path $config)) {
    throw "EVREN configuration file not found."
}

$env:KILO_CONFIG = $config
$env:OPENCODE_CONFIG = $config

# Create reports directory
$reports = Join-Path $PSScriptRoot "reports"
New-Item -ItemType Directory -Path $reports -Force | Out-Null

# Synthetic example - no real CV data
$example = 'public int add(int a, int b) { return a - b; }'

Write-Host "STEP 1: Kilo Code review"

$kiloPrompt = "Review this Java method: $example Explain the bug in two sentences. Do not read or modify any files."

$kiloResult = & kilo run --model $model --agent plan $kiloPrompt | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "Kilo Code failed: $kiloResult"
}

$kiloResult | Set-Content (Join-Path $reports "kilo-review.md") -Encoding UTF8

Write-Host "STEP 2: OpenCode review"

$openPrompt = "Independently review this Java method: $example Explain the bug and expected behavior in two sentences. Do not read or modify files."

$openResult = & opencode run --model $model --agent plan $openPrompt | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "OpenCode failed: $openResult"
}

$openResult | Set-Content (Join-Path $reports "opencode-review.md") -Encoding UTF8

Write-Host "STEP 3: EVREN final evaluation"

$finalPrompt = @"
Compare these two Java code reviews.
Write a concise final report in Turkish.
Identify the bug and the correct solution.
Do not read or modify any files.

KILO REVIEW:
$kiloResult

OPENCODE REVIEW:
$openResult
"@

$finalResult = & opencode run --model $model --agent plan $finalPrompt | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "Final evaluation failed: $finalResult"
}

$finalResult | Set-Content (Join-Path $reports "final-report.md") -Encoding UTF8

Write-Host "ORCHESTRATION COMPLETED"
Write-Host "Reports saved to: $reports"
