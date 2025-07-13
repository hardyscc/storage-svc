#!/bin/bash

# MinIO CLI Setup Script for macOS
# This script installs MinIO CLI if it's not already available

echo "🔧 MinIO CLI Setup for Storage Service Testing"
echo "=============================================="

# Check if MinIO CLI is already installed
if command -v mc &> /dev/null; then
    echo "✅ MinIO CLI is already installed"
    mc --version
    echo
    echo "💡 You can now run the test scripts:"
    echo "   ./quick-test-minio.sh     - Quick test"
    echo "   ./test-minio-cli.sh       - Comprehensive test"
    exit 0
fi

echo "📦 MinIO CLI not found. Installing..."

# Check if Homebrew is available
if command -v brew &> /dev/null; then
    echo "🍺 Installing MinIO CLI via Homebrew..."
    brew install minio/stable/mc
    echo "✅ MinIO CLI installed via Homebrew"
else
    echo "🌐 Installing MinIO CLI manually..."
    
    # Detect architecture
    ARCH=$(uname -m)
    case $ARCH in
        x86_64)
            MC_URL="https://dl.min.io/client/mc/release/darwin-amd64/mc"
            ;;
        arm64)
            MC_URL="https://dl.min.io/client/mc/release/darwin-arm64/mc"
            ;;
        *)
            echo "❌ Unsupported architecture: $ARCH"
            exit 1
            ;;
    esac
    
    # Download and install
    echo "📥 Downloading MinIO CLI..."
    curl -L $MC_URL -o mc
    chmod +x mc
    sudo mv mc /usr/local/bin/
    echo "✅ MinIO CLI installed manually"
fi

# Verify installation
echo "🔍 Verifying installation..."
if command -v mc &> /dev/null; then
    echo "✅ MinIO CLI successfully installed"
    mc --version
    echo
    echo "🎉 Setup complete! You can now run:"
    echo "   ./quick-test-minio.sh     - Quick test"
    echo "   ./test-minio-cli.sh       - Comprehensive test"
else
    echo "❌ MinIO CLI installation failed"
    echo "   Please install manually from: https://min.io/docs/minio/linux/reference/minio-mc.html"
    exit 1
fi
