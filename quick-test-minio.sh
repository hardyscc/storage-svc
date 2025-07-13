#!/bin/bash

# Quick MinIO CLI Test for Storage Service
# A simplified version for quick testing

set -e

# Configuration
SERVICE_URL="http://localhost:9000"
ACCESS_KEY="minioadmin"
SECRET_KEY="minioadmin"
ALIAS_NAME="storage-quick"

echo "🚀 Quick MinIO CLI Test for Storage Service"
echo "==========================================="

# Check if service is running
echo "📡 Checking if storage service is running..."
if ! curl -s $SERVICE_URL/actuator/health > /dev/null; then
    echo "❌ Storage service is not running on $SERVICE_URL"
    echo "   Please start the service with: mvn spring-boot:run"
    exit 1
fi
echo "✅ Storage service is running"

# Check MinIO CLI
echo "🔧 Checking MinIO CLI..."
if ! command -v mc &> /dev/null; then
    echo "❌ MinIO CLI (mc) is not installed"
    echo "   Install it with: brew install minio/stable/mc"
    exit 1
fi
echo "✅ MinIO CLI is available"

# Configure alias
echo "⚙️  Configuring MinIO CLI..."
mc alias set $ALIAS_NAME $SERVICE_URL $ACCESS_KEY $SECRET_KEY
echo "✅ Alias configured"

# Test basic operations
echo "📂 Testing basic operations..."

echo "  1. Listing buckets..."
mc ls $ALIAS_NAME

echo "  2. Creating test bucket..."
mc mb $ALIAS_NAME/quick-test 2>/dev/null || echo "    (bucket may already exist)"

echo "  3. Creating and uploading test file..."
echo "Hello from MinIO CLI test!" > quick-test.txt
mc cp quick-test.txt $ALIAS_NAME/quick-test/

echo "  4. Listing bucket contents..."
mc ls $ALIAS_NAME/quick-test

echo "  5. Downloading file..."
mc cp $ALIAS_NAME/quick-test/quick-test.txt downloaded-quick-test.txt

echo "  6. Verifying file integrity..."
if cmp -s quick-test.txt downloaded-quick-test.txt; then
    echo "    ✅ File integrity verified"
else
    echo "    ❌ File integrity check failed"
    exit 1
fi

echo "  7. Cleaning up..."
mc rm $ALIAS_NAME/quick-test/quick-test.txt
mc rb $ALIAS_NAME/quick-test
rm -f quick-test.txt downloaded-quick-test.txt

echo
echo "🎉 Quick test completed successfully!"
echo "   Your storage service is working with MinIO CLI!"
echo
echo "💡 Run ./test-minio-cli.sh for comprehensive testing"
