package com.storagesvc.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Utility class for detecting content types based on file extensions.
 * This provides S3-compatible content-type detection for stored objects.
 */
@Component
public class ContentTypeDetector {

    private static final Map<String, String> CONTENT_TYPE_MAP = createContentTypeMap();

    private static Map<String, String> createContentTypeMap() {
        Map<String, String> map = new HashMap<>();

        // Documents
        map.put(".pdf", "application/pdf");
        map.put(".doc", "application/msword");
        map.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        map.put(".xls", "application/vnd.ms-excel");
        map.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        map.put(".ppt", "application/vnd.ms-powerpoint");
        map.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // Images
        map.put(".jpg", "image/jpeg");
        map.put(".jpeg", "image/jpeg");
        map.put(".png", "image/png");
        map.put(".gif", "image/gif");
        map.put(".bmp", "image/bmp");
        map.put(".svg", "image/svg+xml");
        map.put(".webp", "image/webp");
        map.put(".ico", "image/x-icon");

        // Text
        map.put(".txt", "text/plain");
        map.put(".html", "text/html");
        map.put(".htm", "text/html");
        map.put(".css", "text/css");
        map.put(".js", "application/javascript");
        map.put(".json", "application/json");
        map.put(".xml", "application/xml");
        map.put(".csv", "text/csv");
        map.put(".md", "text/markdown");
        map.put(".yaml", "application/x-yaml");
        map.put(".yml", "application/x-yaml");

        // Archives
        map.put(".zip", "application/zip");
        map.put(".tar", "application/x-tar");
        map.put(".gz", "application/gzip");
        map.put(".bz2", "application/x-bzip2");
        map.put(".7z", "application/x-7z-compressed");
        map.put(".rar", "application/vnd.rar");

        // Audio
        map.put(".mp3", "audio/mpeg");
        map.put(".wav", "audio/wav");
        map.put(".ogg", "audio/ogg");
        map.put(".m4a", "audio/mp4");
        map.put(".flac", "audio/flac");

        // Video
        map.put(".mp4", "video/mp4");
        map.put(".avi", "video/x-msvideo");
        map.put(".mov", "video/quicktime");
        map.put(".wmv", "video/x-ms-wmv");
        map.put(".flv", "video/x-flv");
        map.put(".webm", "video/webm");
        map.put(".mkv", "video/x-matroska");

        // Other common types
        map.put(".bin", "application/octet-stream");
        map.put(".exe", "application/octet-stream");
        map.put(".dmg", "application/x-apple-diskimage");
        map.put(".iso", "application/x-iso9660-image");
        map.put(".log", "text/plain");

        return map;
    }

    /**
     * Detects the content type based on the file extension.
     * 
     * @param filename The filename or object key
     * @return The detected content type or "application/octet-stream" as default
     */
    public MediaType detectContentType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        // Extract file extension
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        String extension = filename.substring(lastDotIndex).toLowerCase();
        String contentType = CONTENT_TYPE_MAP.get(extension);

        if (contentType != null) {
            return MediaType.parseMediaType(contentType);
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Gets the content type as a string.
     * 
     * @param filename The filename or object key
     * @return The detected content type string
     */
    public String detectContentTypeString(String filename) {
        return detectContentType(filename).toString();
    }
}
