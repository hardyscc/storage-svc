package com.storagesvc.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.storagesvc.config.StorageConfig;
import com.storagesvc.model.Bucket;
import com.storagesvc.model.Delete;
import com.storagesvc.model.DeleteResult;
import com.storagesvc.model.ListBucketResult;
import com.storagesvc.model.S3Object;
import com.storagesvc.util.ChunkedTransferDecoder;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageConfig storageConfig;

    @PostConstruct
    private void init() {
        this.storageConfig.ensureDirectoriesExist();
    }

    public List<Bucket> listBuckets() {
        File rootDir = new File(storageConfig.getRootPath());
        File[] bucketDirs = rootDir.listFiles(File::isDirectory);

        if (bucketDirs == null) {
            return new ArrayList<>();
        }

        return Arrays.stream(bucketDirs)
                .map(dir -> new Bucket(dir.getName(), Instant.ofEpochMilli(dir.lastModified())))
                .collect(Collectors.toList());
    }

    public boolean createBucket(String bucketName) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        return bucketDir.mkdirs() || bucketDir.exists();
    }

    public boolean deleteBucket(String bucketName) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        if (bucketDir.exists() && bucketDir.isDirectory()) {
            // First, recursively remove any empty directories
            removeEmptyDirectoriesRecursively(bucketDir);

            // List all remaining files and directories
            String[] files = bucketDir.list();
            if (files != null && files.length > 0) {
                // Log what files are preventing deletion for debugging
                System.out.println("Bucket deletion failed - remaining files: " + Arrays.toString(files));
                return false; // Bucket not empty
            }

            // Delete the directory
            return bucketDir.delete();
        }
        return false;
    }

    /**
     * Recursively removes empty directories from a bucket directory.
     * This method traverses the directory tree and removes empty directories
     * from bottom to top (post-order traversal).
     */
    private void removeEmptyDirectoriesRecursively(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recursively process subdirectories first
                    removeEmptyDirectoriesRecursively(file);

                    // Try to delete the directory if it's empty
                    String[] contents = file.list();
                    if (contents != null && contents.length == 0) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            System.out.println("Removed empty directory: " + file.getPath());
                        }
                    }
                }
            }
        }
    }

    public boolean bucketExists(String bucketName) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        return bucketDir.exists() && bucketDir.isDirectory();
    }

    public List<S3Object> listObjects(String bucketName, String prefix, int maxKeys) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        if (!bucketDir.exists()) {
            return new ArrayList<>();
        }

        try {
            return Files.walk(bucketDir.toPath())
                    .filter(Files::isRegularFile)
                    .map(this::pathToS3Object)
                    .filter(obj -> prefix == null || obj.getKey().startsWith(prefix))
                    .limit(maxKeys > 0 ? maxKeys : 1000)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public String putObject(String bucketName, String key, InputStream inputStream, long contentLength)
            throws IOException {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        if (!bucketDir.exists()) {
            throw new IllegalArgumentException("Bucket does not exist: " + bucketName);
        }

        File objectFile = new File(bucketDir, key);
        objectFile.getParentFile().mkdirs();

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 not available", e);
        }

        // Check if the input stream is using AWS S3 chunked transfer encoding
        InputStream actualInputStream = inputStream;
        if (inputStream.markSupported() && ChunkedTransferDecoder.isChunkedTransferEncoding(inputStream)) {
            actualInputStream = ChunkedTransferDecoder.decode(inputStream);
        }

        try (FileOutputStream fos = new FileOutputStream(objectFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = actualInputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                md5.update(buffer, 0, bytesRead);
            }
        } finally {
            // Close the decoded stream if it's different from the original
            if (actualInputStream != inputStream) {
                actualInputStream.close();
            }
        }

        byte[] digest = md5.digest();
        StringBuilder etag = new StringBuilder();
        for (byte b : digest) {
            etag.append(String.format("%02x", b));
        }

        return "\"" + etag.toString() + "\"";
    }

    public InputStream getObject(String bucketName, String key) throws IOException {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        if (!objectFile.exists()) {
            throw new FileNotFoundException("Object not found: " + key);
        }
        return new FileInputStream(objectFile);
    }

    public InputStream getObject(String bucketName, String key, long offset, long length) throws IOException {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        if (!objectFile.exists()) {
            throw new FileNotFoundException("Object not found: " + key);
        }

        FileInputStream fis = new FileInputStream(objectFile);
        if (offset > 0) {
            long skipped = fis.skip(offset);
            if (skipped != offset) {
                fis.close();
                throw new IOException("Failed to skip to offset: " + offset);
            }
        }

        // Return a limited input stream that only reads the specified length
        return new LimitedInputStream(fis, length);
    }

    // Helper class to limit the number of bytes read from an InputStream
    private static class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        public LimitedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = delegate.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int result = delegate.read(b, off, toRead);
            if (result > 0) {
                remaining -= result;
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    public boolean deleteObject(String bucketName, String key) {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        return objectFile.exists() && objectFile.delete();
    }

    public boolean objectExists(String bucketName, String key) {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        return objectFile.exists() && objectFile.isFile();
    }

    public long getObjectSize(String bucketName, String key) {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        return objectFile.exists() ? objectFile.length() : 0;
    }

    public Instant getObjectLastModified(String bucketName, String key) {
        File objectFile = new File(storageConfig.getRootPath(), bucketName + "/" + key);
        return objectFile.exists() ? Instant.ofEpochMilli(objectFile.lastModified()) : null;
    }

    private S3Object pathToS3Object(Path path) {
        try {
            File file = path.toFile();
            String key = getRelativeKey(path);
            long size = file.length();
            Instant lastModified = Instant.ofEpochMilli(file.lastModified());

            // Generate simple ETag for the file
            String etag = "\"" + Integer.toHexString((key + size + lastModified.toString()).hashCode()) + "\"";

            return new S3Object(key, lastModified, etag, size);
        } catch (Exception e) {
            return null;
        }
    }

    private String getRelativeKey(Path path) {
        Path rootPath = Paths.get(storageConfig.getRootPath());
        Path relativePath = rootPath.relativize(path);
        String[] parts = relativePath.toString().split(File.separator);

        // Skip the bucket name (first part) and return the object key
        if (parts.length > 1) {
            return String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }

    public DeleteResult deleteObjects(String bucketName, Delete deleteRequest) {
        DeleteResult deleteResult = new DeleteResult();

        for (Delete.ObjectIdentifier objectId : deleteRequest.getObjects()) {
            String key = objectId.getKey();
            boolean deleted = deleteObject(bucketName, key);

            if (deleted) {
                DeleteResult.DeletedObject deletedObject = new DeleteResult.DeletedObject();
                deletedObject.setKey(key);
                deleteResult.getDeleted().add(deletedObject);
            } else {
                // In S3, if an object doesn't exist, it's still considered successfully deleted
                // unless there's a specific error
                DeleteResult.DeletedObject deletedObject = new DeleteResult.DeletedObject();
                deletedObject.setKey(key);
                deleteResult.getDeleted().add(deletedObject);
            }
        }

        return deleteResult;
    }

    public ListBucketResult listBucket(String bucketName, String prefix, int maxKeys) {
        List<S3Object> objects = listObjects(bucketName, prefix, maxKeys);

        ListBucketResult result = new ListBucketResult();
        result.setName(bucketName);
        result.setPrefix(prefix != null ? prefix : "");
        result.setMaxKeys(maxKeys > 0 ? maxKeys : 1000);
        result.setContents(objects);
        result.setTruncated(false);

        return result;
    }

    // Multipart upload methods
    public String initiateMultipartUpload(String bucketName, String key) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        if (!bucketDir.exists()) {
            throw new IllegalArgumentException("Bucket does not exist: " + bucketName);
        }

        // Generate a unique upload ID (in production, this should be more robust)
        String uploadId = System.currentTimeMillis() + "-" + Math.random();

        // Create multipart upload directory
        File multipartDir = new File(bucketDir, ".multipart-uploads/" + uploadId);
        multipartDir.mkdirs();

        // Store metadata about the upload
        File metadataFile = new File(multipartDir, "_metadata");
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            String metadata = "key=" + key + "\n" +
                    "uploadId=" + uploadId + "\n" +
                    "created=" + Instant.now().toString() + "\n";
            fos.write(metadata.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create multipart upload metadata", e);
        }

        return uploadId;
    }

    public String uploadPart(String bucketName, String key, String uploadId, int partNumber,
            InputStream inputStream, long contentLength) throws IOException {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        File multipartDir = new File(bucketDir, ".multipart-uploads/" + uploadId);

        if (!multipartDir.exists()) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }

        File partFile = new File(multipartDir, "part-" + String.format("%05d", partNumber));

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 not available", e);
        }

        try (FileOutputStream fos = new FileOutputStream(partFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                md5.update(buffer, 0, bytesRead);
            }
        }

        byte[] digest = md5.digest();
        StringBuilder etag = new StringBuilder();
        for (byte b : digest) {
            etag.append(String.format("%02x", b));
        }

        return "\"" + etag.toString() + "\"";
    }

    public String completeMultipartUpload(String bucketName, String key, String uploadId) throws IOException {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        File multipartDir = new File(bucketDir, ".multipart-uploads/" + uploadId);

        if (!multipartDir.exists()) {
            throw new IllegalArgumentException("Upload ID not found: " + uploadId);
        }

        // List all part files and sort them
        File[] partFiles = multipartDir.listFiles((dir, name) -> name.startsWith("part-"));
        if (partFiles == null || partFiles.length == 0) {
            throw new IllegalArgumentException("No parts found for upload: " + uploadId);
        }

        // Sort parts by part number
        Arrays.sort(partFiles, (a, b) -> {
            String aNum = a.getName().substring(5); // Remove "part-" prefix
            String bNum = b.getName().substring(5);
            return Integer.compare(Integer.parseInt(aNum), Integer.parseInt(bNum));
        });

        // Create the final object file
        File objectFile = new File(bucketDir, key);
        objectFile.getParentFile().mkdirs();

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new IOException("MD5 not available", e);
        }

        // Combine all parts into the final file
        try (FileOutputStream fos = new FileOutputStream(objectFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            for (File partFile : partFiles) {
                try (FileInputStream fis = new FileInputStream(partFile);
                        BufferedInputStream bis = new BufferedInputStream(fis)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        md5.update(buffer, 0, bytesRead);
                    }
                }
            }
        }

        // Clean up multipart upload directory
        for (File partFile : partFiles) {
            partFile.delete();
        }
        new File(multipartDir, "_metadata").delete();
        multipartDir.delete();

        byte[] digest = md5.digest();
        StringBuilder etag = new StringBuilder();
        for (byte b : digest) {
            etag.append(String.format("%02x", b));
        }

        return "\"" + etag.toString() + "\"";
    }

    public void abortMultipartUpload(String bucketName, String key, String uploadId) {
        File bucketDir = new File(storageConfig.getRootPath(), bucketName);
        File multipartDir = new File(bucketDir, ".multipart-uploads/" + uploadId);

        if (multipartDir.exists()) {
            // Clean up all part files
            File[] partFiles = multipartDir.listFiles();
            if (partFiles != null) {
                for (File file : partFiles) {
                    file.delete();
                }
            }
            multipartDir.delete();
        }
    }
}
