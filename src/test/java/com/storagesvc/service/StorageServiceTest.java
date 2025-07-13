package com.storagesvc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.storagesvc.config.StorageConfig;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;
    private StorageConfig storageConfig;

    @BeforeEach
    void setUp() throws IOException {
        storageConfig = new StorageConfig();
        ReflectionTestUtils.setField(storageConfig, "rootPath", tempDir.toString());
        ReflectionTestUtils.setField(storageConfig, "bucketMetadataPath", tempDir.resolve("metadata").toString());

        storageService = new StorageService(storageConfig);

        // Clean up any existing bucket directories
        cleanupBuckets();
    }

    private void cleanupBuckets() throws IOException {
        File rootDir = new File(tempDir.toString());
        if (rootDir.exists()) {
            File[] bucketDirs = rootDir.listFiles(File::isDirectory);
            if (bucketDirs != null) {
                for (File bucketDir : bucketDirs) {
                    if (!bucketDir.getName().equals("metadata")) {
                        deleteDirectory(bucketDir);
                    }
                }
            }
        }
    }

    private void deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }

    @Test
    void testCreateAndListBuckets() {
        // Initially no buckets
        assertEquals(0, storageService.listBuckets().size());

        // Create a bucket
        assertTrue(storageService.createBucket("test-bucket"));
        assertTrue(storageService.bucketExists("test-bucket"));

        // List buckets
        var buckets = storageService.listBuckets();
        assertEquals(1, buckets.size());
        assertEquals("test-bucket", buckets.get(0).getName());
    }

    @Test
    void testPutAndGetObject() throws IOException {
        // Create bucket first
        storageService.createBucket("test-bucket");

        // Put object
        String content = "Hello World";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        String etag = storageService.putObject("test-bucket", "test.txt", inputStream, content.length());

        assertNotNull(etag);
        assertTrue(storageService.objectExists("test-bucket", "test.txt"));

        // Get object
        try (var objectStream = storageService.getObject("test-bucket", "test.txt")) {
            String retrievedContent = new String(objectStream.readAllBytes());
            assertEquals(content, retrievedContent);
        }
    }

    @Test
    void testListObjects() throws IOException {
        // Create bucket and add objects
        storageService.createBucket("test-bucket");

        String content = "test";
        ByteArrayInputStream inputStream1 = new ByteArrayInputStream(content.getBytes());
        storageService.putObject("test-bucket", "file1.txt", inputStream1, content.length());

        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(content.getBytes());
        storageService.putObject("test-bucket", "folder/file2.txt", inputStream2, content.length());

        // List objects
        var objects = storageService.listObjects("test-bucket", null, 100);
        assertEquals(2, objects.size());

        // List with prefix
        var prefixedObjects = storageService.listObjects("test-bucket", "folder/", 100);
        assertEquals(1, prefixedObjects.size());
        assertEquals("folder/file2.txt", prefixedObjects.get(0).getKey());
    }

    @Test
    void testDeleteObject() throws IOException {
        // Create bucket and object
        storageService.createBucket("test-bucket");

        String content = "test";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        storageService.putObject("test-bucket", "test.txt", inputStream, content.length());

        assertTrue(storageService.objectExists("test-bucket", "test.txt"));

        // Delete object
        assertTrue(storageService.deleteObject("test-bucket", "test.txt"));
        assertFalse(storageService.objectExists("test-bucket", "test.txt"));
    }

    @Test
    void testDeleteBucket() {
        // Create bucket
        storageService.createBucket("test-bucket");
        assertTrue(storageService.bucketExists("test-bucket"));

        // Delete empty bucket
        assertTrue(storageService.deleteBucket("test-bucket"));
        assertFalse(storageService.bucketExists("test-bucket"));
    }
}
