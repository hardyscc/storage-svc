package com.storagesvc.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JacksonXmlRootElement(localName = "Contents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class S3Object {

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "LastModified")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastModified;

    @JacksonXmlProperty(localName = "ETag")
    private String etag;

    @JacksonXmlProperty(localName = "Size")
    private long size;

    @JacksonXmlProperty(localName = "StorageClass")
    private String storageClass = "STANDARD";

    public S3Object(String key, Instant lastModified, String etag, long size) {
        this.key = key;
        this.lastModified = lastModified;
        this.etag = etag;
        this.size = size;
        this.storageClass = "STANDARD";
    }
}
