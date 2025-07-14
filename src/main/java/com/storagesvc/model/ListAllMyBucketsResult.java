package com.storagesvc.model;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;
import lombok.NoArgsConstructor;

@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
@Data
public class ListAllMyBucketsResult {

    @JacksonXmlProperty(localName = "Owner")
    private Owner owner;

    @JacksonXmlProperty(localName = "Buckets")
    private BucketList buckets;

    public ListAllMyBucketsResult() {
        this.owner = new Owner();
        this.buckets = new BucketList();
    }

    @Data
    @NoArgsConstructor
    public static class Owner {
        @JacksonXmlProperty(localName = "ID")
        private String id = "minioadmin";

        @JacksonXmlProperty(localName = "DisplayName")
        private String displayName = "minioadmin";
    }

    @Data
    @NoArgsConstructor
    public static class BucketList {
        @JacksonXmlProperty(localName = "Bucket")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<Bucket> bucket;
    }
}
