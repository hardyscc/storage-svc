# MinIO Java Client Integration Tests

This directory contains comprehensive integration tests for the Storage Service using the MinIO Java Client library. These tests validate the complete S3-compatible API functionality and performance characteristics of the storage service.

## Test Classes Overview

### 1. MinioClientIntegrationTest

**Comprehensive functional testing of all S3 operations**

- **Bucket Operations**: Create, list, delete, and check bucket existence
- **Object Operations**: Upload, download, delete, and get metadata for objects
- **Nested Folder Structures**: Test hierarchical object organization with prefixes
- **Object Copying**: Copy objects within and between buckets
- **Large File Handling**: Upload and download large files (up to 10MB) with integrity verification
- **Concurrent Operations**: Multi-threaded upload/download operations
- **Error Handling**: Comprehensive error scenarios and edge cases
- **Data Integrity**: Verify file content integrity and metadata accuracy

### 2. MinioClientUseCaseTest

**Real-world usage scenarios and business use cases**

- **Document Management System**: Organize documents by type, date, and category
- **Web Application File Uploads**: User profile pictures, documents, and temporary files
- **Multi-tenant Storage**: Isolated storage per tenant with proper data segregation
- **Backup and Archival Workflow**: Daily, weekly, and monthly backup patterns
- **File Processing Pipeline**: Staged file processing (incoming → processing → processed → archived)
- **Content Delivery**: Web assets, static files, and API data with proper content types

### 3. MinioClientPerformanceTest

**Performance benchmarking and stress testing**

- **Concurrent Small Files**: Upload 50 small files concurrently and measure throughput
- **Large File Performance**: Test 1MB, 5MB, and 10MB file upload/download speeds
- **Download Stress Test**: 50 concurrent downloads to test server capacity
- **Memory Efficiency**: Stream large files and test range requests
- **Rapid Operations**: Fast create/delete cycles to test operation overhead

## Prerequisites

1. **Java 17+** - Required for Spring Boot 3
2. **Maven 3.6+** - For building and running tests
3. **Storage Service Running** - The service must be running on localhost:9000

## Running the Tests

### Start the Storage Service

```bash
# Build the project
mvn clean compile

# Start the storage service
mvn spring-boot:run
```

The service will start on port 9000 with default credentials:

- Access Key: `minioadmin`
- Secret Key: `minioadmin`

### Run All Integration Tests

```bash
# Run all integration tests
mvn test -Dtest="com.storagesvc.integration.*"
```

### Run Individual Test Classes

#### Functional Integration Tests

```bash
mvn test -Dtest="MinioClientIntegrationTest"
```

#### Use Case Tests

```bash
mvn test -Dtest="MinioClientUseCaseTest"
```

#### Performance Tests

```bash
mvn test -Dtest="MinioClientPerformanceTest"
```

### Run Specific Test Methods

```bash
# Test only bucket operations
mvn test -Dtest="MinioClientIntegrationTest#testBucketOperations"

# Test document management use case
mvn test -Dtest="MinioClientUseCaseTest#testDocumentManagementSystem"

# Test concurrent upload performance
mvn test -Dtest="MinioClientPerformanceTest#testConcurrentSmallFileUploads"
```

## Test Configuration

### Test Profiles

Tests use the `test` profile by default with the following configuration:

- Server port: Random available port (managed by Spring Boot Test)
- Storage path: Temporary directory (cleaned up after tests)
- Access/Secret keys: minioadmin/minioadmin

### Test Data

All tests create and clean up their own test data:

- Bucket names are prefixed with `test-` or `demo-`
- Object names include timestamps or unique identifiers
- Large test files are generated programmatically
- All test data is automatically cleaned up after each test

## Performance Benchmarks

### Expected Performance Metrics

#### Small File Operations (< 1KB)

- **Upload Rate**: 10-50 files/second (depending on concurrency)
- **Download Rate**: 20-100 files/second
- **Average Latency**: < 500ms per operation

#### Large File Operations (1-10MB)

- **Upload Throughput**: > 1 MB/s
- **Download Throughput**: > 5 MB/s
- **Memory Usage**: Efficient streaming (constant memory usage)

#### Concurrent Operations

- **50 Concurrent Uploads**: Complete within 30 seconds
- **50 Concurrent Downloads**: Complete within 45 seconds
- **No Failed Operations**: All operations should succeed

### Performance Test Output

The performance tests provide detailed output including:

```
Concurrent Small File Upload Performance:
  Files uploaded: 50
  Total time: 15342 ms
  Average upload time: 287.23 ms
  Total bytes: 2850 bytes
  Throughput: 0.18 MB/s
  Files per second: 3.26
```

## Troubleshooting

### Common Issues

#### Service Not Running

```
Error: Storage service is not running on http://localhost:9000
```

**Solution**: Start the storage service with `mvn spring-boot:run`

#### Port Conflicts

```
Error: Port 9000 is already in use
```

**Solution**: Kill the existing process or change the port in `application.properties`

#### Memory Issues During Performance Tests

```
OutOfMemoryError during large file tests
```

**Solution**: Increase JVM heap size: `export MAVEN_OPTS="-Xmx2g"`

#### Test Timeout

```
Test timeout after 30 seconds
```

**Solution**: The storage service might be slow. Check disk space and system resources.

### Debug Mode

Enable debug logging for detailed test execution:

```bash
mvn test -Dtest="MinioClientIntegrationTest" -Dlogging.level.com.storagesvc=DEBUG
```

## Test Coverage

### API Coverage

- ✅ **GET /** - List buckets
- ✅ **PUT /{bucket}** - Create bucket
- ✅ **DELETE /{bucket}** - Delete bucket
- ✅ **GET /{bucket}** - List objects
- ✅ **PUT /{bucket}/{object}** - Upload object
- ✅ **GET /{bucket}/{object}** - Download object
- ✅ **DELETE /{bucket}/{object}** - Delete object
- ✅ **HEAD /{bucket}/{object}** - Get object metadata

### Feature Coverage

- ✅ **Authentication** - AWS Signature V4
- ✅ **Content Types** - Proper MIME type handling
- ✅ **Nested Paths** - Folder-like object organization
- ✅ **Large Files** - Multi-megabyte file handling
- ✅ **Concurrent Access** - Thread safety
- ✅ **Error Handling** - Proper HTTP status codes
- ✅ **Data Integrity** - Content verification

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-tests:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: "17"
          distribution: "temurin"

      - name: Run Storage Service
        run: |
          mvn spring-boot:run &
          sleep 30  # Wait for service to start

      - name: Run Integration Tests
        run: mvn test -Dtest="com.storagesvc.integration.*"

      - name: Generate Test Report
        if: always()
        run: mvn surefire-report:report
```

## Contributing

When adding new tests:

1. **Follow Naming Conventions**: Use descriptive test method names with `@DisplayName`
2. **Clean Up Resources**: Always clean up test data in `@AfterEach`
3. **Use Test Data Prefixes**: Prefix test buckets with `test-` or `demo-`
4. **Add Performance Assertions**: Include reasonable performance expectations
5. **Document Use Cases**: Add clear comments explaining the business scenario

## Further Reading

- [MinIO Java Client Documentation](https://min.io/docs/minio/linux/developers/java/minio-java.html)
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [AWS S3 API Reference](https://docs.aws.amazon.com/s3/latest/API/Welcome.html)
