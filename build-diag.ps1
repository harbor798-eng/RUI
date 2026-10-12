# build-diag.ps1 - Build a complete diagnostic RUI app-image
# Usage: powershell -ExecutionPolicy Bypass -File build-diag.ps1
# Does NOT touch target/release/RUI.

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$jdk = "C:\Program Files\Java\jdk-26.0.1"
$env:JAVA_HOME = $jdk
$jpackage = Join-Path $jdk "bin\jpackage.exe"
$jarName = "relationship-assistant-1.0.0.jar"
$mainClass = "com.harbor.relationshipassistant.ui.ChatViewLauncher"

$releaseApp = "target/release/RUI/app"
$releaseRuntime = "target/release/RUI/runtime"
$stagingDir = "target/diag-staging"
$finalDir = "target/diag-build"
$finalRui = Join-Path $finalDir "RUI"

function Log($msg) {
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$ts] $msg"
}

function Fail($msg) {
    Write-Host "BUILD FAILED: $msg" -ForegroundColor Red
    exit 1
}

# === Step 1: Maven build ===
Log "Step 1: mvn package"
& mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Fail "mvn package failed (exit $LASTEXITCODE)" }

$builtJar = Join-Path "target" $jarName
if (-not (Test-Path $builtJar)) { Fail "built JAR not found: $builtJar" }
$jarHash = (Get-FileHash $builtJar -Algorithm SHA256).Hash
$jarSize = (Get-Item $builtJar).Length
Log "JAR: $builtJar size=$jarSize sha256=$jarHash"

# === Step 2: clean staging ===
Log "Step 2: prepare staging"
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory -Path $stagingDir | Out-Null

# refresh JAR in jpackage-input
Copy-Item $builtJar "target/jpackage-input/$jarName" -Force

# === Step 3: jpackage (no --runtime-image; runtime copied from release) ===
Log "Step 3: jpackage"
& $jpackage --type app-image --name RUI `
    --input "target/jpackage-input" `
    --main-jar $jarName `
    --main-class $mainClass `
    --dest $stagingDir 2>&1 | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { Fail "jpackage failed (exit $LASTEXITCODE)" }

$stagedRui = Join-Path $stagingDir "RUI"
if (-not (Test-Path (Join-Path $stagedRui "RUI.exe"))) { Fail "jpackage output missing RUI.exe" }

# === Step 4: deploy resources ===
Log "Step 4: deploy resources"
$appDir = Join-Path $stagedRui "app"

# 4a: overwrite JAR with fresh build
Copy-Item $builtJar (Join-Path $appDir $jarName) -Force

# 4b: web from source
$srcWeb = "src/main/resources/web"
$dstWeb = Join-Path $appDir "web"
if (Test-Path $dstWeb) { Remove-Item $dstWeb -Recurse -Force }
Copy-Item $srcWeb $dstWeb -Recurse -Force

# 4c: skills from release
Copy-Item (Join-Path $releaseApp "skills") (Join-Path $appDir "skills") -Recurse -Force

# 4d: python-runtime from release
Copy-Item (Join-Path $releaseApp "python-runtime") (Join-Path $appDir "python-runtime") -Recurse -Force

# 4e: wechatapi-main from release
Copy-Item (Join-Path $releaseApp "wechatapi-main") (Join-Path $appDir "wechatapi-main") -Recurse -Force

# 4f: runtime from release
$dstRuntime = Join-Path $stagedRui "runtime"
if (Test-Path $dstRuntime) { Remove-Item $dstRuntime -Recurse -Force }
Copy-Item $releaseRuntime $dstRuntime -Recurse -Force

# === Step 5: integrity checks ===
Log "Step 5: integrity checks"
$errors = @()

if (-not (Test-Path (Join-Path $appDir $jarName))) { $errors += "JAR missing" }
if (-not (Test-Path (Join-Path $appDir "RUI.cfg"))) { $errors += "RUI.cfg missing" }
if (-not (Test-Path (Join-Path $appDir "web\index.html"))) { $errors += "web/index.html missing" }
if (-not (Test-Path (Join-Path $appDir "skills\goutoujunshi\SKILL.md"))) { $errors += "skills/goutoujunshi missing" }
if (-not (Test-Path (Join-Path $appDir "skills\tong-jincheng-skill-main\SKILL.md"))) { $errors += "skills/tong-jincheng missing" }
if (-not (Test-Path (Join-Path $appDir "python-runtime\python.exe"))) { $errors += "python-runtime/python.exe missing" }
if (-not (Test-Path (Join-Path $appDir "wechatapi-main"))) { $errors += "wechatapi-main missing" }
if (-not (Test-Path (Join-Path $stagedRui "runtime\bin\java.exe"))) { $errors += "runtime/bin/java.exe missing" }

# verify JAR contains system-host.md
$jarList = & (Join-Path $jdk "bin\jar.exe") tf (Join-Path $appDir $jarName)
if ($jarList -notcontains "prompts/system-host.md") { $errors += "JAR missing prompts/system-host.md" }

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host "  MISSING: $_" -ForegroundColor Red }
    Fail "integrity checks failed"
}
Log "All integrity checks passed."

# === Step 6: atomic swap into final diag-build ===
Log "Step 6: publish to $finalDir"
if (Test-Path $finalDir) {
    $backup = "$finalDir.bak"
    if (Test-Path $backup) { Remove-Item $backup -Recurse -Force }
    Move-Item $finalDir $backup
}
try {
    Move-Item $stagingDir $finalDir
    if (Test-Path $backup) { Remove-Item $backup -Recurse -Force }
} catch {
    if (Test-Path $backup) { Move-Item $backup $finalDir }
    Fail "failed to move staging: $_"
}

Log "DONE: $finalRui\RUI.exe"
