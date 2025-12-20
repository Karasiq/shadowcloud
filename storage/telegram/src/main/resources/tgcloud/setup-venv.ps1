# Setup Python environment for Telegram storage using uv
# Installs to ~/.shadowcloud/telegram/.venv-windows

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ShadowCloudDir = Join-Path $env:USERPROFILE ".shadowcloud\telegram"
$VenvName = ".venv-windows"
$VenvDir = Join-Path $ShadowCloudDir $VenvName
$RequirementsFile = Join-Path $ScriptDir "requirements.txt"

function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

Write-ColorOutput "=== Telegram Storage Python Setup ===" "Cyan"
Write-ColorOutput "Installing to: $VenvDir" "Cyan"
Write-Host ""

# Check if Python exists
$pythonCmd = $null
$pythonVersions = @("python3.12", "python3.11", "python3.10", "python3", "python")

foreach ($py in $pythonVersions) {
    $cmd = Get-Command $py -ErrorAction SilentlyContinue
    if ($cmd) {
        $pythonCmd = $cmd.Source
        break
    }
}

if (-not $pythonCmd) {
    Write-ColorOutput "✗ Python 3 not found" "Red"
    Write-Host "Please install Python 3.12+ from https://www.python.org/"
    exit 1
}

# Get Python version
$pythonVersionOutput = & $pythonCmd --version 2>&1
Write-ColorOutput "✓ Found $pythonVersionOutput" "Green"

# Verify Python version
$versionMatch = $pythonVersionOutput -match "Python (\d+)\.(\d+)"
if ($versionMatch) {
    $majorVersion = [int]$Matches[1]
    $minorVersion = [int]$Matches[2]

    if ($majorVersion -lt 3 -or ($majorVersion -eq 3 -and $minorVersion -lt 7)) {
        Write-ColorOutput "✗ Python 3.7+ required, found $pythonVersionOutput" "Red"
        exit 1
    }

    if ($majorVersion -eq 3 -and $minorVersion -lt 12) {
        Write-ColorOutput "⚠ Python 3.12+ recommended" "Yellow"
    }
}

# Check if uv is installed
Write-Host ""
$uvCmd = Get-Command uv -ErrorAction SilentlyContinue

if (-not $uvCmd) {
    Write-ColorOutput "⚠ uv not found, installing..." "Yellow"

    try {
        irm https://astral.sh/uv/install.ps1 | iex

        $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

        $uvCmd = Get-Command uv -ErrorAction SilentlyContinue
        if (-not $uvCmd) {
            Write-ColorOutput "✗ uv installation failed" "Red"
            exit 1
        }

        Write-ColorOutput "✓ uv installed successfully" "Green"
    } catch {
        Write-ColorOutput "✗ Failed to install uv: $_" "Red"
        exit 1
    }
} else {
    $uvVersion = & uv --version 2>&1
    Write-ColorOutput "✓ Found $uvVersion" "Green"
}

# Create .shadowcloud/telegram directory
Write-Host ""
Write-ColorOutput "Creating directory: $ShadowCloudDir" "Cyan"
New-Item -ItemType Directory -Force -Path $ShadowCloudDir | Out-Null

# Remove existing venv if it exists
if (Test-Path $VenvDir) {
    Write-ColorOutput "⚠ Removing existing venv" "Yellow"
    Remove-Item -Recurse -Force $VenvDir
}

# Create .venv with Python 3.12
Write-Host ""
Write-ColorOutput "Creating virtual environment..." "Cyan"

$python312 = Get-Command python3.12 -ErrorAction SilentlyContinue
$python311 = Get-Command python3.11 -ErrorAction SilentlyContinue
$python310 = Get-Command python3.10 -ErrorAction SilentlyContinue

if ($python312) {
    Write-ColorOutput "✓ Using Python 3.12" "Green"
    & uv venv $VenvDir --python python3.12
} elseif ($python311) {
    Write-ColorOutput "⚠ Python 3.12 not found, using Python 3.11" "Yellow"
    & uv venv $VenvDir --python python3.11
} elseif ($python310) {
    Write-ColorOutput "⚠ Python 3.12 not found, using Python 3.10" "Yellow"
    & uv venv $VenvDir --python python3.10
} else {
    Write-ColorOutput "⚠ Python 3.12 not found, using default Python" "Yellow"
    & uv venv $VenvDir --python $pythonCmd
}

if (-not (Test-Path $VenvDir)) {
    Write-ColorOutput "✗ Failed to create virtual environment" "Red"
    exit 1
}

Write-ColorOutput "✓ Virtual environment created" "Green"

# Install dependencies
Write-Host ""
Write-ColorOutput "Installing dependencies..." "Cyan"

$venvPython = Join-Path $VenvDir "Scripts\python.exe"
& uv pip install --python $venvPython -r $RequirementsFile

if ($LASTEXITCODE -ne 0) {
    Write-ColorOutput "✗ Failed to install dependencies" "Red"
    exit 1
}

Write-ColorOutput "✓ Dependencies installed successfully" "Green"

# Verify installation
Write-Host ""
Write-ColorOutput "Verifying installation..." "Cyan"

$installedPackages = & $venvPython -m pip list | Select-String -Pattern "(telethon|opentele|cryptg)"

if (-not $installedPackages) {
    Write-ColorOutput "✗ Verification failed - packages not found" "Red"
    exit 1
}

Write-ColorOutput "✓ Installed packages:" "Green"
$installedPackages | ForEach-Object { Write-Host "  $_" }

# Summary
Write-Host ""
Write-ColorOutput "=== Setup Complete ===" "Green"
Write-Host ""
Write-Host "Virtual environment: $VenvDir"
Write-Host "Python executable: $venvPython"
Write-Host ""
