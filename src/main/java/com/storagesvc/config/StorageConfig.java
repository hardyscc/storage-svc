package com.storagesvc.config;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Value("${storage.root-path}")
    private String rootPath;

    @Value("${storage.bucket-metadata-path}")
    private String bucketMetadataPath;

    public String getRootPath() {
        return rootPath;
    }

    public String getBucketMetadataPath() {
        return bucketMetadataPath;
    }

    public void ensureDirectoriesExist() {
        File rootDir = new File(rootPath);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        File metadataDir = new File(bucketMetadataPath);
        if (!metadataDir.exists()) {
            metadataDir.mkdirs();
        }
    }
}
