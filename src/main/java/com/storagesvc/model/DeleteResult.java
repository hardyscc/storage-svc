package com.storagesvc.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "DeleteResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class DeleteResult {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Deleted")
    private List<DeletedObject> deleted = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Error")
    private List<DeleteError> errors = new ArrayList<>();

    public List<DeletedObject> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<DeletedObject> deleted) {
        this.deleted = deleted;
    }

    public List<DeleteError> getErrors() {
        return errors;
    }

    public void setErrors(List<DeleteError> errors) {
        this.errors = errors;
    }

    public static class DeletedObject {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        @JacksonXmlProperty(localName = "DeleteMarker")
        private boolean deleteMarker = false;

        @JacksonXmlProperty(localName = "DeleteMarkerVersionId")
        private String deleteMarkerVersionId;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }

        public boolean isDeleteMarker() {
            return deleteMarker;
        }

        public void setDeleteMarker(boolean deleteMarker) {
            this.deleteMarker = deleteMarker;
        }

        public String getDeleteMarkerVersionId() {
            return deleteMarkerVersionId;
        }

        public void setDeleteMarkerVersionId(String deleteMarkerVersionId) {
            this.deleteMarkerVersionId = deleteMarkerVersionId;
        }
    }

    public static class DeleteError {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "Code")
        private String code;

        @JacksonXmlProperty(localName = "Message")
        private String message;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }
    }
}
