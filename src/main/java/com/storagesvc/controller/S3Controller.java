package com.storagesvc.controller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class S3Controller {

    private static final Logger logger = LoggerFactory.getLogger(S3Controller.class);
    private final StorageService storageService;

    public S3Controller(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListAllMyBucketsResult> listBuckets() {
        List<Bucket> buckets = storageService.listBuckets();

        ListAllMyBucketsResult result = new ListAllMyBucketsResult();
        result.getBuckets().setBucket(buckets);

        return ResponseEntity.ok(result);
    }

    @PutMapping({ "/{bucketName}", "/{bucketName}/" })
    public ResponseEntity<Void> createBucket(@PathVariable String bucketName) {
        if (storageService.bucketExists(bucketName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
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
        result.setIsTruncated(false);
        result.setContents(objects);

        return ResponseEntity.ok(result);
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

        InputStream inputStream = storageService.getObject(bucketName, key);
        long contentLength = storageService.getObjectSize(bucketName, key);
        Instant lastModified = storageService.getObjectLastModified(bucketName, key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(contentLength);
        if (lastModified != null) {
            headers.setLastModified(lastModified);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
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

        boolean deleted = storageService.deleteObject(bucketName, key);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(contentLength);
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
            logger.error("Failed to parse delete request", e);
            return ResponseEntity.badRequest().build();
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
}
