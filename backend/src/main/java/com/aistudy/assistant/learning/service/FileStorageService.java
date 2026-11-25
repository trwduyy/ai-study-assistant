package com.aistudy.assistant.learning.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    StoredFile storeFile(MultipartFile file, Long userId);

    Resource loadFileAsResource(String storedPath);

    boolean deleteFile(String storedPath);

    Path resolvePath(String storedPath);

    record StoredFile(
            String sanitizedOriginalName,
            String mimeType,
            long sizeBytes,
            String relativeStoredPath,
            Path absolutePath,
            String contentHash
    ) {
    }
}
