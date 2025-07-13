package com.storagesvc.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "ListBucketResult")
public class ListBucketResult {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Prefix")
    private String prefix;

    @JsonProperty("Marker")
    private String marker;

    @JsonProperty("MaxKeys")
    private int maxKeys;

    @JsonProperty("IsTruncated")
    private boolean isTruncated;

    @JsonProperty("Contents")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<S3Object> contents;

    public ListBucketResult() {
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    public boolean isIsTruncated() {
        return isTruncated;
    }

    public void setIsTruncated(boolean isTruncated) {
        this.isTruncated = isTruncated;
    }

    public List<S3Object> getContents() {
        return contents;
    }

    public void setContents(List<S3Object> contents) {
        this.contents = contents;
    }
}
