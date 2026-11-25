# CV ATS Analyzer - Real Project AI Review
$ErrorActionPreference = "Stop"

[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$model = "evren/deepseek-v4.1-flash"
$config = "$env:USERPROFILE\.config\opencode-evren\opencode.json"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$reports = Join-Path $PSScriptRoot "project-reports"

if ([string]::IsNullOrWhiteSpace($env:EVREN_API_KEY)) {
    throw "EVREN API key is missing."
}

if (-not (Test-Path $config)) {
    throw "EVREN configuration is missing."
}

$env:KILO_CONFIG = $config
$env:OPENCODE_CONFIG = $config

New-Item -ItemType Directory -Path $reports -Force | Out-Null

Set-Location $root

# STEP 1 - Kilo Code
Write-Host "STEP 1: Kilo Code - Java code review"

$kiloPrompt = @"
Review these project files:
src/main/java/com/cvats/ATSAnalyzer.java
src/main/java/com/cvats/AnalysisResult.java
src/main/java/com/cvats/CriterionResult.java
src/test/java/com/cvats/ATSAnalyzerTest.java

Focus on:
- ATS analysis logic
- Regex accuracy and false positives
- Exception handling
- Missing test cases

Read the listed files.
Do not modify any files.
Do not read CV documents or personal data.
Report evidence with file names and method names.
Respond in English.
"@

$kiloResult = & kilo run --model $model --agent plan $kiloPrompt | Out-String

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($kiloResult)) {
    throw "Kilo review failed."
}

$kiloResult | Set-Content (Join-Path $reports "kilo-project-review.md") -Encoding UTF8

# STEP 2 - OpenCode
Write-Host "STEP 2: OpenCode - OCR review"

$openPrompt = @"
Independently review these project files:
src/main/java/com/cvats/OCRProcessor.java
src/test/java/com/cvats/OCRProcessorTest.java
src/test/java/com/cvats/PageLevelOcrTest.java
tools/OcrBenchmark.java

Focus on:
- OCR accuracy and error handling
- Phone number recognition errors
- SQL versus SOL recognition
- PDF extraction and page-level OCR
- Test coverage and regressions

Read the listed files.
Do not modify any files.
Do not read CV documents or personal data.
Report evidence with file names and method names.
Respond in English.
"@

$openResult = & opencode run --model $model --agent plan $openPrompt | Out-String

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($openResult)) {
    throw "OpenCode review failed."
}

$openResult | Set-Content (Join-Path $reports "opencode-project-review.md") -Encoding UTF8

# STEP 3 - Final evaluation
Write-Host "STEP 3: EVREN - Final project report"

$finalPrompt = @"
Compare the following AI code reviews.

The existing Maven baseline is:
75 tests passed, 0 failures, 0 errors.

Create a concise report in Turkish including:
1. Confirmed findings and their evidence
2. Possible issues requiring verification
3. Missing tests
4. Prioritized improvement recommendations

Do not claim an issue is proven without code evidence.
Do not modify any files.
Do not read additional files.

KILO CODE REVIEW:
$kiloResult

OPENCODE REVIEW:
$openResult
"@

$finalResult = & opencode run --model $model --agent plan $finalPrompt | Out-String

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($finalResult)) {
    throw "Final evaluation failed."
}

$finalResult | Set-Content (Join-Path $reports "final-project-report.md") -Encoding UTF8

Write-Host "PROJECT REVIEW COMPLETED"
Write-Host "Reports saved to: $reports"
