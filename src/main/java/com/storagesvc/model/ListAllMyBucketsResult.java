package com.storagesvc.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
public class ListAllMyBucketsResult {

    @JsonProperty("Owner")
    private Owner owner;

    @JsonProperty("Buckets")
    private BucketList buckets;

    public ListAllMyBucketsResult() {
        this.owner = new Owner();
        this.buckets = new BucketList();
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public BucketList getBuckets() {
        return buckets;
    }

    public void setBuckets(BucketList buckets) {
        this.buckets = buckets;
    }

    public static class Owner {
        @JsonProperty("ID")
        private String id = "minioadmin";

        @JsonProperty("DisplayName")
        private String displayName = "minioadmin";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class BucketList {
        @JsonProperty("Bucket")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<Bucket> bucket;

        public List<Bucket> getBucket() {
            return bucket;
        }

        public void setBucket(List<Bucket> bucket) {
            this.bucket = bucket;
        }
    }
}
