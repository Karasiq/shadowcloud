#!/bin/bash
# Setup Python environment for Telegram storage using uv
# Installs to ~/.shadowcloud/telegram/.venv-{os}

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHADOWCLOUD_DIR="${HOME}/.shadowcloud/telegram"
VENV_NAME=".venv-$(uname -s | tr '[:upper:]' '[:lower:]')"
VENV_DIR="$SHADOWCLOUD_DIR/$VENV_NAME"
REQUIREMENTS_FILE="$SCRIPT_DIR/requirements.txt"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Telegram Storage Python Setup ===${NC}"
echo -e "${INFO}Installing to: $VENV_DIR${NC}"
echo ""

# Check if Python exists
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}✗ Python 3 not found${NC}"
    echo "Please install Python 3.12+ from https://www.python.org/"
    exit 1
fi

PYTHON_VERSION=$(python3 -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')
echo -e "${GREEN}✓ Found Python $PYTHON_VERSION${NC}"

# Check Python version
PYTHON_MAJOR=$(python3 -c 'import sys; print(sys.version_info.major)')
PYTHON_MINOR=$(python3 -c 'import sys; print(sys.version_info.minor)')

if [ "$PYTHON_MAJOR" -lt 3 ] || ([ "$PYTHON_MAJOR" -eq 3 ] && [ "$PYTHON_MINOR" -lt 7 ]); then
    echo -e "${RED}✗ Python 3.7+ required, found $PYTHON_VERSION${NC}"
    exit 1
fi

if [ "$PYTHON_MAJOR" -eq 3 ] && [ "$PYTHON_MINOR" -lt 12 ]; then
    echo -e "${YELLOW}⚠ Python 3.12+ recommended, found $PYTHON_VERSION${NC}"
fi

# Check if uv is installed
echo ""
if ! command -v uv &> /dev/null; then
    echo -e "${YELLOW}⚠ uv not found, installing...${NC}"

    if command -v curl &> /dev/null; then
        curl -LsSf https://astral.sh/uv/install.sh | sh
    elif command -v wget &> /dev/null; then
        wget -qO- https://astral.sh/uv/install.sh | sh
    else
        echo -e "${RED}✗ Neither curl nor wget found${NC}"
        echo "Please install uv manually: curl -LsSf https://astral.sh/uv/install.sh | sh"
        exit 1
    fi

    export PATH="$HOME/.cargo/bin:$PATH"

    if ! command -v uv &> /dev/null; then
        echo -e "${RED}✗ uv installation failed${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ uv installed successfully${NC}"
else
    UV_VERSION=$(uv --version 2>&1 | head -n 1)
    echo -e "${GREEN}✓ Found $UV_VERSION${NC}"
fi

# Create .shadowcloud/telegram directory
echo ""
echo -e "${BLUE}Creating directory: $SHADOWCLOUD_DIR${NC}"
mkdir -p "$SHADOWCLOUD_DIR"

# Remove existing venv if it exists
if [ -d "$VENV_DIR" ]; then
    echo -e "${YELLOW}⚠ Removing existing venv${NC}"
    rm -rf "$VENV_DIR"
fi

# Create .venv with Python 3.12
echo ""
echo -e "${BLUE}Creating virtual environment...${NC}"

if command -v python3.12 &> /dev/null; then
    echo -e "${GREEN}✓ Using Python 3.12${NC}"
    uv venv "$VENV_DIR" --python python3.12
elif command -v python3.11 &> /dev/null; then
    echo -e "${YELLOW}⚠ Python 3.12 not found, using Python 3.11${NC}"
    uv venv "$VENV_DIR" --python python3.11
elif command -v python3.10 &> /dev/null; then
    echo -e "${YELLOW}⚠ Python 3.12 not found, using Python 3.10${NC}"
    uv venv "$VENV_DIR" --python python3.10
else
    echo -e "${YELLOW}⚠ Python 3.12 not found, using default Python 3${NC}"
    uv venv "$VENV_DIR" --python python3
fi

if [ ! -d "$VENV_DIR" ]; then
    echo -e "${RED}✗ Failed to create virtual environment${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Virtual environment created${NC}"

# Install dependencies
echo ""
echo -e "${BLUE}Installing dependencies...${NC}"

VENV_PYTHON="$VENV_DIR/bin/python"
uv pip install --python "$VENV_PYTHON" -r "$REQUIREMENTS_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Dependencies installed successfully${NC}"
else
    echo -e "${RED}✗ Failed to install dependencies${NC}"
    exit 1
fi

# Verify installation
echo ""
echo -e "${BLUE}Verifying installation...${NC}"

INSTALLED_PACKAGES=$($VENV_PYTHON -m pip list | grep -E "(telethon|opentele|cryptg)" || true)

if [ -z "$INSTALLED_PACKAGES" ]; then
    echo -e "${RED}✗ Verification failed - packages not found${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Installed packages:${NC}"
echo "$INSTALLED_PACKAGES"

# Summary
echo ""
echo -e "${GREEN}=== Setup Complete ===${NC}"
echo ""
echo "Virtual environment: $VENV_DIR"
echo "Python executable: $VENV_PYTHON"
echo ""
echo "To activate manually:"
echo -e "  ${BLUE}source $VENV_DIR/bin/activate${NC}"
echo ""
