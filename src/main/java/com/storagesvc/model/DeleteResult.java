package com.storagesvc.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;
import lombok.NoArgsConstructor;

@JacksonXmlRootElement(localName = "DeleteResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@Data
@NoArgsConstructor
public class DeleteResult {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Deleted")
    private List<DeletedObject> deleted = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Error")
    private List<DeleteError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class DeletedObject {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        @JacksonXmlProperty(localName = "DeleteMarker")
        private boolean deleteMarker = false;

        @JacksonXmlProperty(localName = "DeleteMarkerVersionId")
        private String deleteMarkerVersionId;
    }

    @Data
    @NoArgsConstructor
    public static class DeleteError {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "Code")
        private String code;

        @JacksonXmlProperty(localName = "Message")
        private String message;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;
    }
}
