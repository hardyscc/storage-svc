# Storage Service - AWS S3 Compatible API

This is a Spring Boot 3 application that provides an AWS S3 compatible API using local file system storage.

## Features

- AWS S3 compatible REST API
- Authentication and authorization using access/secret keys
- Local file system storage
- Support for bucket operations (create, delete, list)
- Support for object operations (put, get, delete, head, list)
- Compatible with MinIO client CLI

## Configuration

The application uses the following default configuration:

- **Port**: 9000
- **Access Key**: minioadmin
- **Secret Key**: minioadmin
- **Storage Path**: ./storage-data
- **Bucket Metadata Path**: ./bucket-metadata

You can customize these settings in `src/main/resources/application.properties`.

## Running the Application

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The service will start on port 9000.

## Testing with MinIO Client

### Install MinIO Client

```bash
# macOS
brew install minio/stable/mc

# Linux
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/
```

### Configure MinIO Client

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
```

### Basic Operations

#### List buckets

```bash
mc ls local
```

#### Create a bucket

```bash
mc mb local/test-bucket
```

#### Upload a file

```bash
echo "Hello World" > test.txt
mc cp test.txt local/test-bucket/
```

#### List objects in bucket

```bash
mc ls local/test-bucket
```

#### Download a file

```bash
mc cp local/test-bucket/test.txt downloaded-test.txt
```

#### Delete a file

```bash
mc rm local/test-bucket/test.txt
```

#### Delete a bucket

```bash
mc rb local/test-bucket
```

## API Endpoints

### Bucket Operations

- `GET /` - List all buckets
- `PUT /{bucketName}` - Create a bucket
- `DELETE /{bucketName}` - Delete a bucket
- `GET /{bucketName}` - List objects in bucket

### Object Operations

- `PUT /{bucketName}/{objectKey}` - Upload an object
- `GET /{bucketName}/{objectKey}` - Download an object
- `DELETE /{bucketName}/{objectKey}` - Delete an object
- `HEAD /{bucketName}/{objectKey}` - Get object metadata

## Authentication

The service uses AWS Signature Version 4 authentication (simplified implementation). The MinIO client handles this automatically when configured with the access and secret keys.

## Storage Structure

Files are stored in the local file system under the configured storage path:

```
./storage-data/
├── bucket1/
│   ├── file1.txt
│   └── folder/
│       └── file2.txt
└── bucket2/
    └── another-file.txt
```

## Health Check

Check application health:

```bash
curl http://localhost:9000/actuator/health
```
