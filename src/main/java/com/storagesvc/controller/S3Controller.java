package com.storagesvc.controller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.storagesvc.model.Bucket;
import com.storagesvc.model.Delete;
import com.storagesvc.model.DeleteResult;
import com.storagesvc.model.ListAllMyBucketsResult;
import com.storagesvc.model.ListBucketResult;
import com.storagesvc.model.S3Object;
import com.storagesvc.service.StorageService;
import com.storagesvc.util.ContentTypeDetector;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class S3Controller {

    private final StorageService storageService;
    private final ContentTypeDetector contentTypeDetector;

    @GetMapping(value = "/", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListAllMyBucketsResult> listBuckets() {
        List<Bucket> buckets = storageService.listBuckets();

        ListAllMyBucketsResult result = new ListAllMyBucketsResult();
        result.getBuckets().setBucket(buckets);

        return ResponseEntity.ok(result);
    }

    @PutMapping({ "/{bucketName}", "/{bucketName}/" })
    public ResponseEntity<Void> createBucket(@PathVariable String bucketName) {
        // S3 bucket creation is idempotent - if bucket already exists, return success
        if (storageService.bucketExists(bucketName)) {
            return ResponseEntity.ok().build();
        }

        boolean created = storageService.createBucket(bucketName);
        return created ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @DeleteMapping({ "/{bucketName}", "/{bucketName}/" })
    public ResponseEntity<Void> deleteBucket(@PathVariable String bucketName) {
        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        boolean deleted = storageService.deleteBucket(bucketName);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @GetMapping(value = { "/{bucketName}", "/{bucketName}/" }, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListBucketResult> listObjects(
            @PathVariable String bucketName,
            @RequestParam(required = false) String prefix,
            @RequestParam(value = "max-keys", required = false, defaultValue = "1000") int maxKeys) {

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        List<S3Object> objects = storageService.listObjects(bucketName, prefix, maxKeys);

        ListBucketResult result = new ListBucketResult();
        result.setName(bucketName);
        result.setPrefix(prefix);
        result.setMaxKeys(maxKeys);
        result.setTruncated(false);
        result.setContents(objects);

        return ResponseEntity.ok(result);
    }

    @GetMapping(value = { "/{bucketName}",
            "/{bucketName}/" }, params = "location", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getBucketLocation(@PathVariable String bucketName) {
        if (!storageService.bucketExists(bucketName)) {
            String errorXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<Error>\n" +
                    "   <Code>NoSuchBucket</Code>\n" +
                    "   <Message>The specified bucket does not exist</Message>\n" +
                    "   <BucketName>" + bucketName + "</BucketName>\n" +
                    "</Error>";
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_XML)
                    .body(errorXml);
        }

        // Return a simple location configuration (us-east-1 is the default region)
        String locationResponse = """
                <?xml version="1.0" encoding="UTF-8"?>
                <LocationConstraint xmlns="http://s3.amazonaws.com/doc/2006-03-01/"/>
                """;

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(locationResponse);
    }

    @PutMapping("/{bucketName}/**")
    public ResponseEntity<Void> putObject(
            @PathVariable String bucketName,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        // Check if this is a copy operation (S3 copy uses x-amz-copy-source header)
        String copySource = request.getHeader("x-amz-copy-source");
        if (copySource != null) {
            return handleCopyObject(bucketName, key, copySource, request, response);
        }

        try (InputStream inputStream = request.getInputStream()) {
            long contentLength = request.getContentLengthLong();

            // Wrap in BufferedInputStream to enable marking for chunked transfer detection
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

            String etag = storageService.putObject(bucketName, key, bufferedInputStream, contentLength);

            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", etag);

            return ResponseEntity.ok().headers(headers).build();
        }
    }

    @GetMapping("/{bucketName}/**")
    public ResponseEntity<InputStreamResource> getObject(
            @PathVariable String bucketName,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        if (!storageService.objectExists(bucketName, key)) {
            return ResponseEntity.notFound().build();
        }

        long totalSize = storageService.getObjectSize(bucketName, key);
        Instant lastModified = storageService.getObjectLastModified(bucketName, key);

        // Check for Range header
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handleRangeRequest(bucketName, key, rangeHeader, totalSize, lastModified);
        }

        // Regular full object request
        InputStream inputStream = storageService.getObject(bucketName, key);

        // Detect content type based on file extension
        MediaType contentType = contentTypeDetector.detectContentType(key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(totalSize);
        if (lastModified != null) {
            headers.setLastModified(lastModified);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(contentType)
                .body(new InputStreamResource(inputStream));
    }

    private ResponseEntity<InputStreamResource> handleRangeRequest(
            String bucketName, String key, String rangeHeader, long totalSize, Instant lastModified)
            throws IOException {

        // Parse Range header (e.g., "bytes=0-1023" or "bytes=1024-")
        String range = rangeHeader.substring(6); // Remove "bytes="
        String[] parts = range.split("-");

        long start = 0;
        long end = totalSize - 1;

        if (parts.length >= 1 && !parts[0].isEmpty()) {
            start = Long.parseLong(parts[0]);
        }
        if (parts.length >= 2 && !parts[1].isEmpty()) {
            end = Long.parseLong(parts[1]);
        }

        // Validate range
        if (start < 0 || end >= totalSize || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + totalSize)
                    .build();
        }

        long contentLength = end - start + 1;
        InputStream inputStream = storageService.getObject(bucketName, key, start, contentLength);

        // Detect content type based on file extension
        MediaType contentType = contentTypeDetector.detectContentType(key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(contentLength);
        headers.set("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
        headers.set("Accept-Ranges", "bytes");
        if (lastModified != null) {
            headers.setLastModified(lastModified);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .contentType(contentType)
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{bucketName}/**")
    public ResponseEntity<Void> deleteObject(
            @PathVariable String bucketName,
            HttpServletRequest request) {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        // S3 DELETE operations are idempotent - always return success regardless of whether object existed
        storageService.deleteObject(bucketName, key);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/{bucketName}/**", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headObject(
            @PathVariable String bucketName,
            HttpServletRequest request) {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        if (!storageService.objectExists(bucketName, key)) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = storageService.getObjectSize(bucketName, key);
        Instant lastModified = storageService.getObjectLastModified(bucketName, key);

        // Detect content type based on file extension
        MediaType contentType = contentTypeDetector.detectContentType(key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(contentLength);
        headers.setContentType(contentType);
        if (lastModified != null) {
            headers.setLastModified(lastModified);
        }

        return ResponseEntity.ok().headers(headers).build();
    }

    @RequestMapping(value = { "/{bucketName}", "/{bucketName}/" }, method = RequestMethod.HEAD)
    public ResponseEntity<Void> headBucket(@PathVariable String bucketName) {
        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    // Bulk delete objects - S3 delete API
    @PostMapping(value = { "/{bucketName}",
            "/{bucketName}/" }, params = "delete", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<DeleteResult> deleteObjects(
            @PathVariable String bucketName,
            @RequestBody String requestBody) {

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        // Parse the XML manually
        try {
            XmlMapper xmlMapper = new XmlMapper();
            Delete deleteRequest = xmlMapper.readValue(requestBody, Delete.class);

            DeleteResult deleteResult = storageService.deleteObjects(bucketName, deleteRequest);
            return ResponseEntity.ok(deleteResult);
        } catch (Exception e) {
            log.error("Failed to parse delete request", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Multipart upload endpoints
    @PostMapping(value = "/{bucketName}/**", params = "uploads", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> initiateMultipartUpload(
            @PathVariable String bucketName,
            HttpServletRequest request) {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String uploadId = storageService.initiateMultipartUpload(bucketName, key);

            String responseXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<InitiateMultipartUploadResult>\n" +
                    "   <Bucket>" + bucketName + "</Bucket>\n" +
                    "   <Key>" + key + "</Key>\n" +
                    "   <UploadId>" + uploadId + "</UploadId>\n" +
                    "</InitiateMultipartUploadResult>";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(responseXml);
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping(value = "/{bucketName}/**", params = { "uploadId", "partNumber" })
    public ResponseEntity<Void> uploadPart(
            @PathVariable String bucketName,
            @RequestParam String uploadId,
            @RequestParam int partNumber,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        try (InputStream inputStream = request.getInputStream()) {
            long contentLength = request.getContentLengthLong();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

            String etag = storageService.uploadPart(bucketName, key, uploadId, partNumber, bufferedInputStream,
                    contentLength);

            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", etag);

            return ResponseEntity.ok().headers(headers).build();
        } catch (Exception e) {
            log.error("Failed to upload part", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{bucketName}/**", params = "uploadId", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> completeMultipartUpload(
            @PathVariable String bucketName,
            @RequestParam String uploadId,
            HttpServletRequest request) {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String etag = storageService.completeMultipartUpload(bucketName, key, uploadId);

            String responseXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<CompleteMultipartUploadResult>\n" +
                    "   <Location>http://localhost/" + bucketName + "/" + key + "</Location>\n" +
                    "   <Bucket>" + bucketName + "</Bucket>\n" +
                    "   <Key>" + key + "</Key>\n" +
                    "   <ETag>" + etag + "</ETag>\n" +
                    "</CompleteMultipartUploadResult>";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(responseXml);
        } catch (Exception e) {
            log.error("Failed to complete multipart upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping(value = "/{bucketName}/**", params = "uploadId")
    public ResponseEntity<Void> abortMultipartUpload(
            @PathVariable String bucketName,
            @RequestParam String uploadId,
            HttpServletRequest request) {

        String key = extractKey(request, bucketName);

        if (!storageService.bucketExists(bucketName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            storageService.abortMultipartUpload(bucketName, key, uploadId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to abort multipart upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String extractKey(HttpServletRequest request, String bucketName) {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();

        // Remove context path if it exists
        if (contextPath != null && !contextPath.isEmpty()) {
            requestURI = requestURI.substring(contextPath.length());
        }

        // Remove leading slash and bucket name
        String key = requestURI.substring(1); // Remove leading /
        if (key.startsWith(bucketName + "/")) {
            key = key.substring(bucketName.length() + 1);
        }

        return key;
    }

    private ResponseEntity<Void> handleCopyObject(
            String destBucketName,
            String destKey,
            String copySource,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        log.debug("Handling copy operation: source={}, dest={}/{}", copySource, destBucketName, destKey);

        // Parse the copy source header (format: /bucket/key or /bucket/key with URL encoding)
        if (copySource.startsWith("/")) {
            copySource = copySource.substring(1); // Remove leading slash
        }

        // URL decode the copy source
        try {
            copySource = java.net.URLDecoder.decode(copySource, "UTF-8");
        } catch (Exception e) {
            log.warn("Failed to URL decode copy source: {}", copySource, e);
            return ResponseEntity.badRequest().build();
        }

        // Extract source bucket and key
        int firstSlash = copySource.indexOf('/');
        if (firstSlash == -1) {
            log.warn("Invalid copy source format: {}", copySource);
            return ResponseEntity.badRequest().build();
        }

        String sourceBucketName = copySource.substring(0, firstSlash);
        String sourceKey = copySource.substring(firstSlash + 1);

        log.debug("Parsed copy source: bucket={}, key={}", sourceBucketName, sourceKey);

        // Validate source bucket and object exist
        if (!storageService.bucketExists(sourceBucketName)) {
            log.warn("Source bucket does not exist: {}", sourceBucketName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!storageService.objectExists(sourceBucketName, sourceKey)) {
            log.warn("Source object does not exist: {}/{}", sourceBucketName, sourceKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Validate destination bucket exists
        if (!storageService.bucketExists(destBucketName)) {
            log.warn("Destination bucket does not exist: {}", destBucketName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Perform the copy operation by reading from source and writing to destination
            InputStream sourceInputStream = storageService.getObject(sourceBucketName, sourceKey);

            // Get source object size
            long contentLength = storageService.getObjectSize(sourceBucketName, sourceKey);

            // Copy the object
            String etag = storageService.putObject(destBucketName, destKey, sourceInputStream, contentLength);

            // Close the source stream
            sourceInputStream.close();

            // Build response with ETag and other headers similar to S3
            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", etag);
            headers.set("x-amz-copy-source-version-id", "null"); // We don't support versioning

            // Set copy result in response body (S3 returns XML with copy result)
            // Format timestamp to be S3-compatible (without nanoseconds)
            String timestamp = Instant.now().toString().replaceAll("\\.[0-9]+Z", "Z");
            String copyResultXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<CopyObjectResult>\n" +
                    "   <LastModified>" + timestamp + "</LastModified>\n" +
                    "   <ETag>" + etag + "</ETag>\n" +
                    "</CopyObjectResult>";

            response.setContentType("application/xml");
            response.getWriter().write(copyResultXml);
            response.getWriter().flush();

            log.debug("Copy operation completed successfully: {}/{} -> {}/{}",
                    sourceBucketName, sourceKey, destBucketName, destKey);

            return ResponseEntity.ok().headers(headers).build();

        } catch (Exception e) {
            log.error("Copy operation failed: {}/{} -> {}/{}",
                    sourceBucketName, sourceKey, destBucketName, destKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
