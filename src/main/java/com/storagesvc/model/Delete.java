package com.storagesvc.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Data;
import lombok.NoArgsConstructor;

@JacksonXmlRootElement(localName = "Delete", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
@Data
@NoArgsConstructor
public class Delete {

    @JacksonXmlProperty(localName = "Quiet")
    private boolean quiet = false;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Object")
    private List<ObjectIdentifier> objects = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class ObjectIdentifier {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;
    }
}
