package com.storagesvc.model;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;
import lombok.NoArgsConstructor;

@JacksonXmlRootElement(localName = "ListBucketResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@Data
@NoArgsConstructor
public class ListBucketResult {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Prefix")
    private String prefix;

    @JacksonXmlProperty(localName = "Marker")
    private String marker;

    @JacksonXmlProperty(localName = "MaxKeys")
    private int maxKeys;

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean isTruncated;

    @JacksonXmlProperty(localName = "Contents")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<S3Object> contents;
}
