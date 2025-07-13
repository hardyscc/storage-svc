#!/bin/bash

# Storage Service - MinIO CLI Comprehensive Test Script
# This script tests all major S3-compatible operations using MinIO CLI

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SERVICE_URL="http://localhost:9000"
ACCESS_KEY="minioadmin"
SECRET_KEY="minioadmin"
ALIAS_NAME="storage-test"

# Test data
TEST_BUCKET_1="test-bucket-1"
TEST_BUCKET_2="test-bucket-2"
TEST_FILE_1="test-file-1.txt"
TEST_FILE_2="test-file-2.json"
TEST_FILE_3="large-test-file.txt"
NESTED_FILE="folder/subfolder/nested-file.txt"

echo -e "${BLUE}===================================================${NC}"
echo -e "${BLUE}      Storage Service - MinIO CLI Test Suite       ${NC}"
echo -e "${BLUE}===================================================${NC}"
echo

# Function to print test step
print_step() {
    echo -e "${YELLOW}[STEP] $1${NC}"
}

# Function to print success
print_success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

# Function to cleanup test data
cleanup() {
    print_step "Cleaning up test data..."
    
    # Remove test files
    rm -f $TEST_FILE_1 $TEST_FILE_2 $TEST_FILE_3 downloaded-* || true
    rm -rf test-folder || true
    
    # Remove buckets and objects (ignore errors if they don't exist)
    mc rm --recursive --force $ALIAS_NAME/$TEST_BUCKET_1/ 2>/dev/null || true
    mc rm --recursive --force $ALIAS_NAME/$TEST_BUCKET_2/ 2>/dev/null || true
    mc rb $ALIAS_NAME/$TEST_BUCKET_1 2>/dev/null || true
    mc rb $ALIAS_NAME/$TEST_BUCKET_2 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# Trap to ensure cleanup on script exit
trap cleanup EXIT

print_step "Checking if storage service is running..."
if ! curl -s $SERVICE_URL/actuator/health > /dev/null; then
    print_error "Storage service is not running on $SERVICE_URL"
    print_error "Please start the service with: mvn spring-boot:run"
    exit 1
fi
print_success "Storage service is running"

print_step "Checking MinIO CLI installation..."
if ! command -v mc &> /dev/null; then
    print_error "MinIO CLI (mc) is not installed"
    echo "Install it with: brew install minio/stable/mc"
    exit 1
fi
print_success "MinIO CLI is installed"

print_step "Configuring MinIO CLI alias..."
mc alias set $ALIAS_NAME $SERVICE_URL $ACCESS_KEY $SECRET_KEY
print_success "Alias '$ALIAS_NAME' configured"

echo
echo -e "${BLUE}=== BUCKET OPERATIONS TESTS ===${NC}"

print_step "1. Listing buckets (should be empty or show existing buckets)..."
mc ls $ALIAS_NAME
print_success "Bucket list retrieved"

print_step "2. Creating test bucket: $TEST_BUCKET_1"
mc mb $ALIAS_NAME/$TEST_BUCKET_1
print_success "Bucket '$TEST_BUCKET_1' created"

print_step "3. Creating second test bucket: $TEST_BUCKET_2"
mc mb $ALIAS_NAME/$TEST_BUCKET_2
print_success "Bucket '$TEST_BUCKET_2' created"

print_step "4. Listing buckets (should show 2 new buckets)..."
mc ls $ALIAS_NAME
print_success "Updated bucket list retrieved"

echo
echo -e "${BLUE}=== OBJECT OPERATIONS TESTS ===${NC}"

print_step "5. Creating test files..."
echo "Hello, World! This is a test file." > $TEST_FILE_1
echo '{"message": "This is a JSON test file", "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'", "test": true}' > $TEST_FILE_2

# Create a larger file for testing
seq 1 1000 | while read i; do echo "Line $i: This is test data for a larger file"; done > $TEST_FILE_3

print_success "Test files created"

print_step "6. Uploading files to bucket..."
mc cp $TEST_FILE_1 $ALIAS_NAME/$TEST_BUCKET_1/
mc cp $TEST_FILE_2 $ALIAS_NAME/$TEST_BUCKET_1/
mc cp $TEST_FILE_3 $ALIAS_NAME/$TEST_BUCKET_1/
print_success "Files uploaded to bucket"

print_step "7. Creating nested folder structure..."
mkdir -p test-folder/subfolder
echo "This is a nested file" > test-folder/subfolder/nested-file.txt
mc cp test-folder/subfolder/nested-file.txt $ALIAS_NAME/$TEST_BUCKET_1/$NESTED_FILE
print_success "Nested file uploaded"

print_step "8. Listing objects in bucket..."
mc ls $ALIAS_NAME/$TEST_BUCKET_1
print_success "Object list retrieved"

print_step "9. Listing objects recursively..."
mc ls --recursive $ALIAS_NAME/$TEST_BUCKET_1
print_success "Recursive object list retrieved"

print_step "10. Getting object metadata (stat)..."
mc stat $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_1
print_success "Object metadata retrieved"

print_step "11. Downloading files..."
mc cp $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_1 downloaded-$TEST_FILE_1
mc cp $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_2 downloaded-$TEST_FILE_2
print_success "Files downloaded"

print_step "12. Verifying downloaded files..."
if cmp -s $TEST_FILE_1 downloaded-$TEST_FILE_1; then
    print_success "Downloaded file 1 matches original"
else
    print_error "Downloaded file 1 does not match original"
    exit 1
fi

if cmp -s $TEST_FILE_2 downloaded-$TEST_FILE_2; then
    print_success "Downloaded file 2 matches original"
else
    print_error "Downloaded file 2 does not match original"
    exit 1
fi

echo
echo -e "${BLUE}=== ADVANCED OPERATIONS TESTS ===${NC}"

print_step "13. Copying objects between buckets..."
mc cp $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_1 $ALIAS_NAME/$TEST_BUCKET_2/copied-$TEST_FILE_1
print_success "Object copied between buckets"

print_step "14. Moving/renaming objects..."
mc cp $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_2 $ALIAS_NAME/$TEST_BUCKET_1/renamed-$TEST_FILE_2
mc rm $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_2
print_success "Object renamed"

print_step "15. Testing object existence..."
if mc stat $ALIAS_NAME/$TEST_BUCKET_1/renamed-$TEST_FILE_2 > /dev/null 2>&1; then
    print_success "Renamed object exists"
else
    print_error "Renamed object not found"
    exit 1
fi

if mc stat $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_2 > /dev/null 2>&1; then
    print_error "Original object still exists (should be deleted)"
    exit 1
else
    print_success "Original object properly deleted"
fi

echo
echo -e "${BLUE}=== CLEANUP AND DELETION TESTS ===${NC}"

print_step "16. Deleting individual objects..."
mc rm $ALIAS_NAME/$TEST_BUCKET_1/$TEST_FILE_1
mc rm $ALIAS_NAME/$TEST_BUCKET_1/renamed-$TEST_FILE_2
print_success "Individual objects deleted"

print_step "17. Deleting objects recursively..."
mc rm --recursive --force $ALIAS_NAME/$TEST_BUCKET_1/folder/
print_success "Nested objects deleted"

print_step "18. Listing bucket after deletions..."
mc ls $ALIAS_NAME/$TEST_BUCKET_1
print_success "Bucket contents listed"

print_step "19. Force deleting bucket with remaining contents..."
mc rm --recursive --force $ALIAS_NAME/$TEST_BUCKET_1/
mc rb $ALIAS_NAME/$TEST_BUCKET_1
print_success "Bucket with contents deleted"

print_step "20. Cleaning up copied file from bucket 2..."
mc rm $ALIAS_NAME/$TEST_BUCKET_2/copied-$TEST_FILE_1
print_success "Copied file deleted from bucket 2"

print_step "21. Deleting empty bucket..."
mc rb $ALIAS_NAME/$TEST_BUCKET_2
print_success "Empty bucket deleted"

print_step "22. Final bucket list (should be empty or back to original state)..."
mc ls $ALIAS_NAME
print_success "Final bucket list retrieved"

echo
echo -e "${BLUE}=== ERROR HANDLING TESTS ===${NC}"

print_step "23. Testing operations on non-existent bucket..."
if mc ls $ALIAS_NAME/non-existent-bucket 2>/dev/null; then
    print_error "Non-existent bucket operation should have failed"
else
    print_success "Non-existent bucket operation properly failed"
fi

print_step "24. Testing download of non-existent object..."
if mc cp $ALIAS_NAME/non-existent-bucket/non-existent-file.txt ./downloaded-non-existent.txt 2>/dev/null; then
    print_error "Non-existent object download should have failed"
else
    print_success "Non-existent object download properly failed"
fi

echo
echo -e "${BLUE}=== PERFORMANCE TEST ===${NC}"

print_step "25. Creating bucket for performance test..."
mc mb $ALIAS_NAME/perf-test

print_step "26. Uploading multiple files..."
for i in {1..10}; do
    echo "Performance test file $i content" > perf-test-$i.txt
    mc cp perf-test-$i.txt $ALIAS_NAME/perf-test/ &
done
wait
print_success "Multiple files uploaded concurrently"

print_step "27. Listing all performance test files..."
mc ls $ALIAS_NAME/perf-test
print_success "Performance test files listed"

print_step "28. Cleaning up performance test..."
mc rm --recursive --force $ALIAS_NAME/perf-test/
mc rb $ALIAS_NAME/perf-test
rm -f perf-test-*.txt
print_success "Performance test cleanup completed"

echo
echo -e "${GREEN}===================================================${NC}"
echo -e "${GREEN}           ALL TESTS COMPLETED SUCCESSFULLY!       ${NC}"
echo -e "${GREEN}===================================================${NC}"
echo
echo -e "${BLUE}Summary of operations tested:${NC}"
echo "✅ Bucket creation and deletion"
echo "✅ Object upload and download"
echo "✅ File integrity verification"
echo "✅ Nested folder structure"
echo "✅ Object metadata retrieval"
echo "✅ Object copying and moving"
echo "✅ Recursive operations"
echo "✅ Error handling"
echo "✅ Concurrent operations"
echo "✅ Complete cleanup"
echo
echo -e "${BLUE}Your storage service is fully compatible with MinIO CLI!${NC}"
