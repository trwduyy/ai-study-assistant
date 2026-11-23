package com.aistudy.assistant.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    StoredFile storeFile(MultipartFile file, Long userId);

    Resource loadFileAsResource(String storagePath);

    boolean deleteFile(String storagePath);

    Path resolvePath(String storagePath);

    record StoredFile(
            String fileName,
            String originalName,
            String fileType,
            long fileSize,
            String storagePath,
            Path absolutePath
    ) {}
}
