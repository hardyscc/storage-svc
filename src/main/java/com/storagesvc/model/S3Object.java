package com.storagesvc.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "Contents")
public class S3Object {

    @JsonProperty("Key")
    private String key;

    @JsonProperty("LastModified")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastModified;

    @JsonProperty("ETag")
    private String etag;

    @JsonProperty("Size")
    private long size;

    @JsonProperty("StorageClass")
    private String storageClass = "STANDARD";

    public S3Object() {
    }

    public S3Object(String key, Instant lastModified, String etag, long size) {
        this.key = key;
        this.lastModified = lastModified;
        this.etag = etag;
        this.size = size;
    }

    // Getters and setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }
}
