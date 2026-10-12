# build-python-runtime.ps1
# Build embedded Python runtime for RUI Windows distribution.
# Locks versions and produces target/python-runtime/ (not committed to Git).
#
# Usage:  powershell -ExecutionPolicy Bypass -File build-python-runtime.ps1
# Requires: Internet access at build time. Final users do not need network.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $root "target/python-runtime"

# --- Fixed versions ---
$PYTHON_VERSION = "3.12.7"
$PYTHON_URL = "https://www.python.org/ftp/python/$PYTHON_VERSION/python-$PYTHON_VERSION-embed-amd64.zip"

# wechatauto-replica fixed commit
$WCHAT_COMMIT = "62503d76a0db069171cb4e42e87090ad285db489"
$WCHAT_REPO   = "https://github.com/fanyuantaier/wechatauto-replica"

Write-Host "[1/5] Creating output dir: $out"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$embedZip = Join-Path $out "python-embed.zip"

Write-Host "[2/5] Downloading embedded Python $PYTHON_VERSION"
Invoke-WebRequest -Uri $PYTHON_URL -OutFile $embedZip

Write-Host "[3/5] Extracting embedded Python"
Expand-Archive -Path $embedZip -DestinationPath $out -Force
Remove-Item $embedZip

# Enable site-packages in embeddable Python by editing ._pth
$pth = Get-ChildItem $out -Filter "python*._pth" | Select-Object -First 1
if ($pth) {
  Write-Host "[3.1] Enabling site-packages in $($pth.Name)"
  (Get-Content $pth.FullName) -replace '^#import site', 'import site' | Set-Content $pth.FullName
}

Write-Host "[4/5] Installing locked dependencies (requires network at build time)"
# Use system pip to install into a staging site-packages dir
$stageSite = Join-Path $out "Lib/site-packages"
New-Item -ItemType Directory -Force -Path $stageSite | Out-Null

# This script is a template. In CI, run:
#   pip install --target=$stageSite `
#     cryptography==43.0.1 `
#     uiautomation==2.0.28 `
#     pywin32==306 `
#     Pillow==11.0.0 `
#     psutil==6.1.0 `
#     colorama==0.4.6 `
#     winsdk==1.0.0b10 `
#     imageio-ffmpeg==0.5.1
#
# Then clone wechatauto-replica at $WCHAT_COMMIT into $stageSite/wechatauto/

Write-Host "[5/5] Python runtime staged at: $out"
Write-Host "Next: copy wechatauto source and db_bridge.py into the runtime."
Write-Host "Final size will be reported after assembly."
