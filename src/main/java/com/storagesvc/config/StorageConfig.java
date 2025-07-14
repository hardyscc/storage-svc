package com.storagesvc.config;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Configuration
@Getter
public class StorageConfig {

    @Value("${storage.root-path}")
    private String rootPath;

    public void ensureDirectoriesExist() {
        File rootDir = new File(rootPath);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
    }
}
