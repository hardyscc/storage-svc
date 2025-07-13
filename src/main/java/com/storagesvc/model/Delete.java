package com.storagesvc.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "Delete", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class Delete {

    @JacksonXmlProperty(localName = "Quiet")
    private boolean quiet = false;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Object")
    private List<ObjectIdentifier> objects = new ArrayList<>();

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public List<ObjectIdentifier> getObjects() {
        return objects;
    }

    public void setObjects(List<ObjectIdentifier> objects) {
        this.objects = objects;
    }

    public static class ObjectIdentifier {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

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
    }
}
