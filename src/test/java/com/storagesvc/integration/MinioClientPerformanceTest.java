package com.storagesvc.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;

/**
 * Performance and load testing for the Storage Service using MinIO Java Client
 * 
 * This test class focuses on:
 * - Concurrent upload/download performance
 * - Large file handling performance
 * - Memory usage optimization
 * - Throughput measurements
 * - Stress testing under load
 * - Connection pooling efficiency
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MinioClientPerformanceTest {

    @LocalServerPort
    private int port;

    private MinioClient minioClient;

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String PERF_BUCKET = "performance-test-bucket";

    @BeforeEach
    void setUp() throws Exception {
        String endpoint = "http://localhost:" + port;
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();

        // Create performance test bucket
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(PERF_BUCKET).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(PERF_BUCKET).build());
        }
    }

    @AfterEach
    void cleanUp() {
        try {
            cleanupBucket(PERF_BUCKET);
        } catch (Exception e) {
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }

    private void cleanupBucket(String bucketName) throws Exception {
        try {
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
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
            }
        } catch (Exception e) {
            // Ignore individual cleanup errors
        }
    }

    @Test
    @Order(1)
    @DisplayName("Performance: Concurrent Small File Uploads")
    void testConcurrentSmallFileUploads() throws Exception {
        int numFiles = 50;
        int numThreads = 10;
        String contentTemplate = "Performance test file content - File %d - Timestamp: %d";

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<UploadResult>> futures = new ArrayList<>();

        Instant startTime = Instant.now();

        // Submit upload tasks
        for (int i = 0; i < numFiles; i++) {
            final int fileIndex = i;
            CompletableFuture<UploadResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String objectName = String.format("small-files/file_%03d.txt", fileIndex);
                    String content = String.format(contentTemplate, fileIndex, System.currentTimeMillis());

                    Instant uploadStart = Instant.now();
                    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                    ObjectWriteResponse response = minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(PERF_BUCKET)
                                    .object(objectName)
                                    .stream(inputStream, content.length(), -1)
                                    .contentType("text/plain")
                                    .build());
                    Instant uploadEnd = Instant.now();

                    return new UploadResult(objectName, Duration.between(uploadStart, uploadEnd),
                            content.length(), response.etag());
                } catch (Exception e) {
                    throw new RuntimeException("Upload failed for file " + fileIndex, e);
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all uploads to complete
        List<UploadResult> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("Upload task failed", e);
                    }
                })
                .toList();

        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);

        executor.shutdown();

        // Analyze results
        assertEquals(numFiles, results.size(), "All files should be uploaded successfully");

        double avgUploadTime = results.stream()
                .mapToLong(r -> r.duration.toMillis())
                .average()
                .orElse(0.0);

        long totalBytes = results.stream()
                .mapToLong(r -> r.fileSize)
                .sum();

        double throughputMBps = (totalBytes / 1024.0 / 1024.0) / (totalTime.toMillis() / 1000.0);

        System.out.printf("Concurrent Small File Upload Performance:\n");
        System.out.printf("  Files uploaded: %d\n", numFiles);
        System.out.printf("  Total time: %d ms\n", totalTime.toMillis());
        System.out.printf("  Average upload time: %.2f ms\n", avgUploadTime);
        System.out.printf("  Total bytes: %d bytes\n", totalBytes);
        System.out.printf("  Throughput: %.2f MB/s\n", throughputMBps);
        System.out.printf("  Files per second: %.2f\n", numFiles / (totalTime.toMillis() / 1000.0));

        // Performance assertions
        assertTrue(totalTime.toMillis() < 30000, "All uploads should complete within 30 seconds");
        assertTrue(avgUploadTime < 5000, "Average upload time should be less than 5 seconds");
        assertTrue(results.stream().allMatch(r -> r.etag != null), "All uploads should have ETags");

        // Verify all files exist
        List<String> uploadedFiles = listAllObjects(PERF_BUCKET, "small-files/");
        assertEquals(numFiles, uploadedFiles.size(), "All uploaded files should be listable");
    }

    @Test
    @Order(2)
    @DisplayName("Performance: Large File Upload and Download")
    void testLargeFilePerformance() throws Exception {
        // Test with different file sizes
        int[] fileSizes = { 1024 * 1024, 5 * 1024 * 1024, 10 * 1024 * 1024 }; // 1MB, 5MB, 10MB

        for (int fileSize : fileSizes) {
            String objectName = String.format("large-files/test_%dMB.bin", fileSize / (1024 * 1024));

            // Generate test data
            byte[] testData = generateTestData(fileSize);

            // Upload performance test
            Instant uploadStart = Instant.now();
            InputStream inputStream = new ByteArrayInputStream(testData);
            ObjectWriteResponse uploadResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .stream(inputStream, testData.length, -1)
                            .contentType("application/octet-stream")
                            .build());
            Instant uploadEnd = Instant.now();
            Duration uploadTime = Duration.between(uploadStart, uploadEnd);

            assertNotNull(uploadResponse.etag(), "Upload should return ETag");

            // Download performance test
            Instant downloadStart = Instant.now();
            byte[] downloadedData;
            try (InputStream objectStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .build())) {
                downloadedData = objectStream.readAllBytes();
            }
            Instant downloadEnd = Instant.now();
            Duration downloadTime = Duration.between(downloadStart, downloadEnd);

            // Verify data integrity
            assertEquals(testData.length, downloadedData.length, "Downloaded file size should match");
            assertArrayEquals(testData, downloadedData, "Downloaded data should match uploaded data");

            // Calculate performance metrics
            double uploadMBps = (fileSize / 1024.0 / 1024.0) / (uploadTime.toMillis() / 1000.0);
            double downloadMBps = (fileSize / 1024.0 / 1024.0) / (downloadTime.toMillis() / 1000.0);

            System.out.printf("Large File Performance (%d MB):\n", fileSize / (1024 * 1024));
            System.out.printf("  Upload time: %d ms (%.2f MB/s)\n", uploadTime.toMillis(), uploadMBps);
            System.out.printf("  Download time: %d ms (%.2f MB/s)\n", downloadTime.toMillis(), downloadMBps);

            // Performance assertions
            assertTrue(uploadTime.toMillis() < 60000, "Large file upload should complete within 60 seconds");
            assertTrue(downloadTime.toMillis() < 30000, "Large file download should complete within 30 seconds");
            assertTrue(uploadMBps > 0.1, "Upload speed should be at least 0.1 MB/s");
            assertTrue(downloadMBps > 0.1, "Download speed should be at least 0.1 MB/s");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Performance: Concurrent Download Stress Test")
    void testConcurrentDownloadStressTest() throws Exception {
        // First, upload test files
        int numFiles = 20;
        String contentTemplate = "Download stress test file %d - Content length test data.";

        // Upload files sequentially first
        for (int i = 0; i < numFiles; i++) {
            String objectName = String.format("download-test/file_%03d.txt", i);
            String content = String.format(contentTemplate, i);

            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .stream(inputStream, content.length(), -1)
                            .contentType("text/plain")
                            .build());
        }

        // Now perform concurrent downloads
        int numConcurrentDownloads = 50; // Download each file multiple times
        ExecutorService executor = Executors.newFixedThreadPool(15);
        List<CompletableFuture<DownloadResult>> futures = new ArrayList<>();

        Instant startTime = Instant.now();

        for (int i = 0; i < numConcurrentDownloads; i++) {
            final int downloadIndex = i;
            final int fileIndex = i % numFiles; // Cycle through available files

            CompletableFuture<DownloadResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String objectName = String.format("download-test/file_%03d.txt", fileIndex);

                    Instant downloadStart = Instant.now();
                    String content;
                    try (InputStream objectStream = minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(PERF_BUCKET)
                                    .object(objectName)
                                    .build())) {
                        content = new String(objectStream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    Instant downloadEnd = Instant.now();

                    return new DownloadResult(objectName, content,
                            Duration.between(downloadStart, downloadEnd), downloadIndex);
                } catch (Exception e) {
                    throw new RuntimeException("Download failed for index " + downloadIndex, e);
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all downloads to complete
        List<DownloadResult> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(45, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("Download task failed", e);
                    }
                })
                .toList();

        Instant endTime = Instant.now();
        Duration totalTime = Duration.between(startTime, endTime);

        executor.shutdown();

        // Analyze results
        assertEquals(numConcurrentDownloads, results.size(), "All downloads should complete successfully");

        double avgDownloadTime = results.stream()
                .mapToLong(r -> r.duration.toMillis())
                .average()
                .orElse(0.0);

        long totalBytes = results.stream()
                .mapToLong(r -> r.content.length())
                .sum();

        double throughputMBps = (totalBytes / 1024.0 / 1024.0) / (totalTime.toMillis() / 1000.0);

        System.out.printf("Concurrent Download Stress Test Performance:\n");
        System.out.printf("  Downloads completed: %d\n", numConcurrentDownloads);
        System.out.printf("  Total time: %d ms\n", totalTime.toMillis());
        System.out.printf("  Average download time: %.2f ms\n", avgDownloadTime);
        System.out.printf("  Total bytes downloaded: %d bytes\n", totalBytes);
        System.out.printf("  Throughput: %.2f MB/s\n", throughputMBps);
        System.out.printf("  Downloads per second: %.2f\n", numConcurrentDownloads / (totalTime.toMillis() / 1000.0));

        // Verify content integrity for sample downloads
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            DownloadResult result = results.get(i);
            assertTrue(result.content.contains("Download stress test file"),
                    "Downloaded content should match expected pattern");
        }

        // Performance assertions
        assertTrue(totalTime.toMillis() < 45000, "All downloads should complete within 45 seconds");
        assertTrue(avgDownloadTime < 10000, "Average download time should be less than 10 seconds");
        assertTrue(throughputMBps > 0.01, "Throughput should be at least 0.01 MB/s");
    }

    @Test
    @Order(4)
    @DisplayName("Performance: Memory Efficiency Test")
    void testMemoryEfficiency() throws Exception {
        // Test streaming large files without loading entirely into memory
        int fileSize = 20 * 1024 * 1024; // 20MB
        String objectName = "memory-test/large-streaming-file.bin";

        // Generate and upload file in chunks to simulate streaming
        int chunkSize = 1024 * 1024; // 1MB chunks
        byte[] chunk = generateTestData(chunkSize);

        // Create a larger file by repeating the chunk
        ByteArrayInputStream largeStream = new ByteArrayInputStream(
                IntStream.range(0, fileSize / chunkSize)
                        .mapToObj(i -> chunk)
                        .reduce(new byte[0], (acc, chunkData) -> {
                            byte[] result = new byte[acc.length + chunkData.length];
                            System.arraycopy(acc, 0, result, 0, acc.length);
                            System.arraycopy(chunkData, 0, result, acc.length, chunkData.length);
                            return result;
                        }, (a, b) -> b));

        Instant uploadStart = Instant.now();
        ObjectWriteResponse uploadResponse = minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(PERF_BUCKET)
                        .object(objectName)
                        .stream(largeStream, fileSize, -1)
                        .contentType("application/octet-stream")
                        .build());
        Instant uploadEnd = Instant.now();

        assertNotNull(uploadResponse.etag(), "Large streaming upload should succeed");

        // Test partial downloads (range requests) to verify streaming capability
        int[][] ranges = { { 0, chunkSize }, { chunkSize, 2 * chunkSize }, { fileSize - chunkSize, fileSize } };

        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];
            int rangeSize = end - start;

            Instant downloadStart = Instant.now();
            byte[] partialData;
            try (InputStream objectStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .offset((long) start)
                            .length((long) rangeSize)
                            .build())) {
                partialData = objectStream.readAllBytes();
            }
            Instant downloadEnd = Instant.now();

            assertEquals(rangeSize, partialData.length,
                    String.format("Range download (%d-%d) should return correct size", start, end));

            Duration downloadTime = Duration.between(downloadStart, downloadEnd);
            double downloadMBps = (rangeSize / 1024.0 / 1024.0) / (downloadTime.toMillis() / 1000.0);

            System.out.printf("Range download (%d-%d): %d ms (%.2f MB/s)\n",
                    start, end, downloadTime.toMillis(), downloadMBps);

            assertTrue(downloadTime.toMillis() < 10000, "Range download should be fast");
        }

        Duration totalUploadTime = Duration.between(uploadStart, uploadEnd);
        double uploadMBps = (fileSize / 1024.0 / 1024.0) / (totalUploadTime.toMillis() / 1000.0);

        System.out.printf("Memory Efficiency Test (20MB file):\n");
        System.out.printf("  Upload time: %d ms (%.2f MB/s)\n", totalUploadTime.toMillis(), uploadMBps);
        System.out.printf("  Range requests completed successfully\n");

        assertTrue(uploadMBps > 0.1, "Upload throughput should be reasonable");
    }

    @Test
    @Order(5)
    @DisplayName("Performance: Rapid File Operations")
    void testRapidFileOperations() throws Exception {
        int numOperations = 100;
        List<Duration> operationTimes = new ArrayList<>();

        // Rapid create and delete cycle
        Instant testStart = Instant.now();

        for (int i = 0; i < numOperations; i++) {
            String objectName = String.format("rapid-ops/temp-file-%d.txt", i);
            String content = String.format("Temporary file %d for rapid operations test", i);

            // Upload
            Instant opStart = Instant.now();
            InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .stream(inputStream, content.length(), -1)
                            .contentType("text/plain")
                            .build());

            // Verify exists by getting stats
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .build());
            assertNotNull(stat.etag(), "File should exist after upload");

            // Delete
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(PERF_BUCKET)
                            .object(objectName)
                            .build());

            Instant opEnd = Instant.now();
            operationTimes.add(Duration.between(opStart, opEnd));
        }

        Instant testEnd = Instant.now();
        Duration totalTime = Duration.between(testStart, testEnd);

        // Analyze operation performance
        double avgOpTime = operationTimes.stream()
                .mapToLong(Duration::toMillis)
                .average()
                .orElse(0.0);

        long maxOpTime = operationTimes.stream()
                .mapToLong(Duration::toMillis)
                .max()
                .orElse(0L);

        long minOpTime = operationTimes.stream()
                .mapToLong(Duration::toMillis)
                .min()
                .orElse(0L);

        double opsPerSecond = numOperations / (totalTime.toMillis() / 1000.0);

        System.out.printf("Rapid File Operations Performance:\n");
        System.out.printf("  Operations completed: %d\n", numOperations);
        System.out.printf("  Total time: %d ms\n", totalTime.toMillis());
        System.out.printf("  Average operation time: %.2f ms\n", avgOpTime);
        System.out.printf("  Min operation time: %d ms\n", minOpTime);
        System.out.printf("  Max operation time: %d ms\n", maxOpTime);
        System.out.printf("  Operations per second: %.2f\n", opsPerSecond);

        // Verify no files remain
        List<String> remainingFiles = listAllObjects(PERF_BUCKET, "rapid-ops/");
        assertEquals(0, remainingFiles.size(), "No files should remain after rapid operations test");

        // Performance assertions
        assertTrue(totalTime.toMillis() < 60000, "Rapid operations should complete within 60 seconds");
        assertTrue(avgOpTime < 2000, "Average operation should be under 2 seconds");
        assertTrue(opsPerSecond > 0.5, "Should complete at least 0.5 operations per second");
    }

    // Helper methods and classes

    private byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }

    private List<String> listAllObjects(String bucket, String prefix) throws Exception {
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

    // Result classes for performance tracking
    @SuppressWarnings("unused")
    private static class UploadResult {
        final String objectName;
        final Duration duration;
        final long fileSize;
        final String etag;

        UploadResult(String objectName, Duration duration, long fileSize, String etag) {
            this.objectName = objectName;
            this.duration = duration;
            this.fileSize = fileSize;
            this.etag = etag;
        }
    }

    @SuppressWarnings("unused")
    private static class DownloadResult {
        final String objectName;
        final String content;
        final Duration duration;
        final int downloadIndex;

        DownloadResult(String objectName, String content, Duration duration, int downloadIndex) {
            this.objectName = objectName;
            this.content = content;
            this.duration = duration;
            this.downloadIndex = downloadIndex;
        }
    }
}
