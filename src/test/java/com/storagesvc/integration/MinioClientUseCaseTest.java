package com.storagesvc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Bucket;
import io.minio.messages.Item;

/**
 * Real-world usage scenario tests for the Storage Service using MinIO Java Client
 * 
 * This test class simulates actual use cases such as:
 * - Document management system scenarios
 * - Web application file uploads
 * - Backup and archival workflows
 * - Multi-tenant storage scenarios
 * - File versioning and metadata management
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MinioClientUseCaseTest {

    @LocalServerPort
    private int port;

    private MinioClient minioClient;

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    @BeforeEach
    void setUp() {
        String endpoint = "http://localhost:" + port;
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
    }

    @AfterEach
    void cleanUp() {
        // Clean up all test buckets
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            for (Bucket bucket : buckets) {
                if (bucket.name().startsWith("test-") || bucket.name().startsWith("demo-")) {
                    cleanupBucket(bucket.name());
                }
            }
        } catch (Exception e) {
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }

    private void cleanupBucket(String bucketName) throws Exception {
        try {
            // Remove all objects first
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build());

            for (Result<Item> result : objects) {
                Item item = result.get();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(item.objectName())
                                .build());
            }

            // Remove the bucket
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            // Ignore individual cleanup errors
        }
    }

    @Test
    @Order(1)
    @DisplayName("Use Case: Document Management System")
    void testDocumentManagementSystem() throws Exception {
        String bucketName = "demo-document-system";
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        // Simulate different document types
        Map<String, String> documents = Map.of(
                "contracts/2024/service_agreement.pdf", "PDF contract content",
                "invoices/2024/01/invoice_001.pdf", "PDF invoice content",
                "reports/quarterly/Q1_2024_report.docx", "Word document content",
                "presentations/2024/company_overview.pptx", "PowerPoint content",
                "templates/invoice_template.xlsx", "Excel template content");

        // Upload documents with appropriate content types
        Map<String, String> contentTypes = Map.of(
                ".pdf", "application/pdf",
                ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        for (Map.Entry<String, String> doc : documents.entrySet()) {
            String objectKey = doc.getKey();
            String content = doc.getValue();
            String extension = objectKey.substring(objectKey.lastIndexOf('.'));
            String contentType = contentTypes.getOrDefault(extension, "application/octet-stream");

            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, content.length(), -1)
                            .contentType(contentType)
                            .build());
        }

        // Test folder-based organization queries
        List<String> contractDocuments = listObjectsWithPrefix(bucketName, "contracts/");
        assertEquals(1, contractDocuments.size(), "Should find 1 contract document");

        List<String> invoiceDocuments = listObjectsWithPrefix(bucketName, "invoices/");
        assertEquals(1, invoiceDocuments.size(), "Should find 1 invoice document");

        List<String> year2024Documents = listObjectsWithPrefix(bucketName, "");
        assertEquals(5, year2024Documents.size(), "Should find all 5 documents");

        // Test document retrieval and content verification
        String contractContent = downloadObject(bucketName, "contracts/2024/service_agreement.pdf");
        assertEquals("PDF contract content", contractContent, "Contract content should match");

        // Test document metadata
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object("contracts/2024/service_agreement.pdf")
                        .build());
        assertEquals("application/pdf", stat.contentType(), "Content type should be PDF");
    }

    @Test
    @Order(2)
    @DisplayName("Use Case: Web Application File Uploads")
    void testWebApplicationFileUploads() throws Exception {
        String bucketName = "demo-webapp-uploads";
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        // Simulate user uploads with different file types
        String userId = "user123";
        String sessionId = "session_abc123";

        // Profile picture upload
        String profilePicture = String.format("users/%s/profile/avatar.jpg", userId);
        uploadImageFile(bucketName, profilePicture, "image/jpeg");

        // Document uploads
        String[] documents = {
                String.format("users/%s/documents/resume.pdf", userId),
                String.format("users/%s/documents/cover_letter.docx", userId),
                String.format("users/%s/documents/portfolio.zip", userId)
        };

        for (String docPath : documents) {
            String extension = docPath.substring(docPath.lastIndexOf('.'));
            String contentType = switch (extension) {
                case ".pdf" -> "application/pdf";
                case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case ".zip" -> "application/zip";
                default -> "application/octet-stream";
            };

            String content = "File content for " + docPath;
            uploadFile(bucketName, docPath, content, contentType);
        }

        // Temporary upload during session
        String tempFile = String.format("temp/%s/upload_in_progress.tmp", sessionId);
        uploadFile(bucketName, tempFile, "Temporary file content", "application/octet-stream");

        // Verify user's files
        List<String> userFiles = listObjectsWithPrefix(bucketName, String.format("users/%s/", userId));
        assertEquals(4, userFiles.size(), "Should have 4 user files (profile + 3 documents)");

        // Verify profile picture
        StatObjectResponse profileStat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object(profilePicture)
                        .build());
        assertEquals("image/jpeg", profileStat.contentType(), "Profile picture should be JPEG");

        // Simulate cleanup of temporary files
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(tempFile)
                        .build());

        // Verify temp file is removed
        assertThrows(ErrorResponseException.class, () -> {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(tempFile)
                            .build());
        }, "Temporary file should be removed");
    }

    @Test
    @Order(3)
    @DisplayName("Use Case: Multi-tenant Storage")
    void testMultiTenantStorage() throws Exception {
        // Create tenant-specific buckets
        String[] tenants = { "tenant-a", "tenant-b", "tenant-c" };

        for (String tenant : tenants) {
            String bucketName = "demo-" + tenant;
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            // Each tenant has their own data structure
            uploadFile(bucketName, "config/app_settings.json",
                    String.format("{\n  \"tenant\": \"%s\",\n  \"version\": \"1.0\"\n}", tenant),
                    "application/json");

            uploadFile(bucketName, "data/users.csv",
                    String.format("id,name,email\n1,User1_%s,user1@%s.com", tenant, tenant),
                    "text/csv");

            uploadFile(bucketName, "logs/app.log",
                    String.format("[INFO] Application started for tenant %s", tenant),
                    "text/plain");
        }

        // Verify tenant isolation
        for (String tenant : tenants) {
            String bucketName = "demo-" + tenant;

            // Verify tenant bucket exists
            assertTrue(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build()),
                    String.format("Bucket for %s should exist", tenant));

            // Verify tenant-specific data
            String config = downloadObject(bucketName, "config/app_settings.json");
            assertTrue(config.contains(tenant),
                    String.format("Config should contain tenant name %s", tenant));

            List<String> objects = listObjectsWithPrefix(bucketName, "");
            assertEquals(3, objects.size(),
                    String.format("Tenant %s should have exactly 3 objects", tenant));
        }

        // Test cross-tenant data access (should be isolated)
        List<Bucket> allBuckets = minioClient.listBuckets();
        List<String> tenantBuckets = allBuckets.stream()
                .map(Bucket::name)
                .filter(name -> name.startsWith("demo-tenant-"))
                .toList();

        assertEquals(3, tenantBuckets.size(), "Should have exactly 3 tenant buckets");
    }

    @Test
    @Order(4)
    @DisplayName("Use Case: Backup and Archival Workflow")
    void testBackupAndArchivalWorkflow() throws Exception {
        String bucketName = "demo-backup-system";
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        // Simulate daily backups with timestamp-based organization
        String today = "2024-07-14";
        String yesterday = "2024-07-13";
        String lastWeek = "2024-07-07";

        // Current day backups
        uploadFile(bucketName, String.format("daily/%s/database_backup.sql", today),
                "-- Database backup for " + today, "text/plain");
        uploadFile(bucketName, String.format("daily/%s/application_logs.tar.gz", today),
                "Binary log archive for " + today, "application/gzip");
        uploadFile(bucketName, String.format("daily/%s/config_backup.json", today),
                String.format("{\"backup_date\": \"%s\", \"type\": \"config\"}", today), "application/json");

        // Previous day backup
        uploadFile(bucketName, String.format("daily/%s/database_backup.sql", yesterday),
                "-- Database backup for " + yesterday, "text/plain");

        // Weekly archive
        uploadFile(bucketName, String.format("weekly/%s/full_system_backup.tar.gz", lastWeek),
                "Full system backup archive", "application/gzip");

        // Monthly archive
        uploadFile(bucketName, "monthly/2024-06/monthly_archive.zip",
                "Monthly archive for June 2024", "application/zip");

        // Test backup retention queries
        List<String> todayBackups = listObjectsWithPrefix(bucketName, String.format("daily/%s/", today));
        assertEquals(3, todayBackups.size(), "Should have 3 backups for today");

        List<String> allDailyBackups = listObjectsWithPrefix(bucketName, "daily/");
        assertEquals(4, allDailyBackups.size(), "Should have 4 daily backups total");

        List<String> weeklyBackups = listObjectsWithPrefix(bucketName, "weekly/");
        assertEquals(1, weeklyBackups.size(), "Should have 1 weekly backup");

        List<String> monthlyBackups = listObjectsWithPrefix(bucketName, "monthly/");
        assertEquals(1, monthlyBackups.size(), "Should have 1 monthly backup");

        // Test backup verification (download and check)
        String configBackup = downloadObject(bucketName, String.format("daily/%s/config_backup.json", today));
        assertTrue(configBackup.contains(today), "Config backup should contain today's date");
        assertTrue(configBackup.contains("\"type\": \"config\""), "Config backup should have correct type");

        // Simulate backup cleanup (remove old daily backups)
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(String.format("daily/%s/database_backup.sql", yesterday))
                        .build());

        // Verify cleanup
        List<String> remainingDailyBackups = listObjectsWithPrefix(bucketName, "daily/");
        assertEquals(3, remainingDailyBackups.size(), "Should have 3 daily backups after cleanup");
    }

    @Test
    @Order(5)
    @DisplayName("Use Case: File Processing Pipeline")
    void testFileProcessingPipeline() throws Exception {
        String bucketName = "demo-processing-pipeline";
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        // Simulate file processing stages: incoming -> processing -> processed -> archived

        // Stage 1: Incoming files
        String[] incomingFiles = {
                "incoming/batch_001/data_file_1.csv",
                "incoming/batch_001/data_file_2.csv",
                "incoming/batch_002/data_file_3.csv"
        };

        for (String file : incomingFiles) {
            String content = String.format("id,name,value\n1,Item1,100\n2,Item2,200\n3,Item3,300");
            uploadFile(bucketName, file, content, "text/csv");
        }

        // Stage 2: Move to processing
        for (String incomingFile : incomingFiles) {
            String processingFile = incomingFile.replace("incoming/", "processing/");

            // Copy to processing folder
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(processingFile)
                            .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(incomingFile)
                                    .build())
                            .build());

            // Remove from incoming
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(incomingFile)
                            .build());
        }

        // Verify files moved to processing
        List<String> processingFiles = listObjectsWithPrefix(bucketName, "processing/");
        assertEquals(3, processingFiles.size(), "Should have 3 files in processing");

        List<String> incomingRemaining = listObjectsWithPrefix(bucketName, "incoming/");
        assertEquals(0, incomingRemaining.size(), "Should have no files remaining in incoming");

        // Stage 3: Process files and move to processed
        for (String processingFile : processingFiles) {
            // Simulate processing by downloading, "processing", and uploading result
            String originalContent = downloadObject(bucketName, processingFile);
            String processedContent = originalContent + "\n# Processed on " + System.currentTimeMillis();

            String processedFile = processingFile.replace("processing/", "processed/");
            uploadFile(bucketName, processedFile, processedContent, "text/csv");

            // Remove from processing
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(processingFile)
                            .build());
        }

        // Stage 4: Archive processed files
        List<String> processedFiles = listObjectsWithPrefix(bucketName, "processed/");
        assertEquals(3, processedFiles.size(), "Should have 3 processed files");

        for (String processedFile : processedFiles) {
            String archivedFile = processedFile.replace("processed/", "archived/");

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(archivedFile)
                            .source(CopySource.builder()
                                    .bucket(bucketName)
                                    .object(processedFile)
                                    .build())
                            .build());
        }

        // Verify final state
        List<String> archivedFiles = listObjectsWithPrefix(bucketName, "archived/");
        assertEquals(3, archivedFiles.size(), "Should have 3 archived files");

        // Verify processed content
        String sampleArchived = archivedFiles.get(0);
        String content = downloadObject(bucketName, sampleArchived);
        assertTrue(content.contains("# Processed on"), "Archived file should contain processing timestamp");
        assertTrue(content.contains("id,name,value"), "Archived file should contain original data");
    }

    @Test
    @Order(6)
    @DisplayName("Use Case: Content Delivery and Caching")
    void testContentDeliveryAndCaching() throws Exception {
        String bucketName = "demo-content-delivery";
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

        // Simulate different types of web content
        Map<String, String> webContent = Map.of(
                "static/css/styles.css", "body { font-family: Arial; }",
                "static/js/app.js", "console.log('Application loaded');",
                "static/images/logo.png", "PNG image data simulation",
                "templates/index.html", "<html><body><h1>Welcome</h1></body></html>",
                "api/data.json", "{\"status\": \"ok\", \"data\": [1,2,3]}");

        Map<String, String> contentTypes = Map.of(
                ".css", "text/css",
                ".js", "application/javascript",
                ".png", "image/png",
                ".html", "text/html",
                ".json", "application/json");

        // Upload content with appropriate headers
        for (Map.Entry<String, String> entry : webContent.entrySet()) {
            String objectKey = entry.getKey();
            String content = entry.getValue();
            String extension = objectKey.substring(objectKey.lastIndexOf('.'));
            String contentType = contentTypes.getOrDefault(extension, "text/plain");

            uploadFile(bucketName, objectKey, content, contentType);
        }

        // Test content retrieval by type
        List<String> staticAssets = listObjectsWithPrefix(bucketName, "static/");
        assertEquals(3, staticAssets.size(), "Should have 3 static assets");

        List<String> cssFiles = listObjectsWithPrefix(bucketName, "static/css/");
        assertEquals(1, cssFiles.size(), "Should have 1 CSS file");

        List<String> jsFiles = listObjectsWithPrefix(bucketName, "static/js/");
        assertEquals(1, jsFiles.size(), "Should have 1 JS file");

        // Verify content types
        StatObjectResponse cssStats = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object("static/css/styles.css")
                        .build());
        assertNotNull(cssStats, "CSS file metadata should be available");
        assertTrue(cssStats.size() > 0, "CSS file should have content");
        assertEquals("text/css", cssStats.contentType(), "Content type should be CSS");

        StatObjectResponse jsStats = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucketName)
                        .object("static/js/app.js")
                        .build());
        assertNotNull(jsStats, "JS file metadata should be available");
        assertTrue(jsStats.size() > 0, "JS file should have content");
        assertEquals("application/javascript", jsStats.contentType(), "Content type should be JavaScript");

        // Test API data retrieval
        String apiData = downloadObject(bucketName, "api/data.json");
        assertTrue(apiData.contains("\"status\": \"ok\""), "API data should contain status");
        assertTrue(apiData.contains("\"data\": [1,2,3]"), "API data should contain data array");

        // Simulate content update (new version deployment)
        String updatedCSS = "body { font-family: Arial; background: #f0f0f0; }";
        uploadFile(bucketName, "static/css/styles.css", updatedCSS, "text/css");

        // Verify update
        String retrievedCSS = downloadObject(bucketName, "static/css/styles.css");
        assertTrue(retrievedCSS.contains("background: #f0f0f0"), "CSS should contain updated styles");
    }

    // Helper methods

    private void uploadFile(String bucket, String objectKey, String content, String contentType) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(inputStream, content.length(), -1)
                        .contentType(contentType)
                        .build());
    }

    private void uploadImageFile(String bucket, String objectKey, String contentType) throws Exception {
        // Simulate image file with some binary-like content
        byte[] imageData = "FAKE_PNG_HEADER\u0089PNG\r\n\u001A\n...image data...".getBytes(StandardCharsets.ISO_8859_1);
        InputStream inputStream = new ByteArrayInputStream(imageData);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(inputStream, imageData.length, -1)
                        .contentType(contentType)
                        .build());
    }

    private String downloadObject(String bucket, String objectKey) throws Exception {
        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {
            return new String(objectStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> listObjectsWithPrefix(String bucket, String prefix) throws Exception {
        List<String> objectNames = new ArrayList<>();
        Iterable<Result<Item>> objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .recursive(true)
                        .build());

        for (Result<Item> result : objects) {
            Item item = result.get();
            objectNames.add(item.objectName());
        }

        return objectNames;
    }
}
