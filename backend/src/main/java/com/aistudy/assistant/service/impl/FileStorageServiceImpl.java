package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L; // 20MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "docx", "txt");

    private final Path rootLocation;

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("File upload storage initialized at: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory: " + rootLocation, e);
        }
    }

    @Override
    public StoredFile storeFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 20MB");
        }

        String rawOriginalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document"
        );

        String extension = getFileExtension(rawOriginalName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException(
                    "Unsupported file format '." + extension + "'. Only .pdf, .docx, and .txt files are allowed"
            );
        }

        validateMimeType(file.getContentType(), extension);

        String sanitizedBaseName = rawOriginalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String uniqueFileName = UUID.randomUUID().toString() + "_" + sanitizedBaseName;

        try {
            Path userDir = this.rootLocation.resolve(String.valueOf(userId)).normalize();
            Files.createDirectories(userDir);

            Path destinationPath = userDir.resolve(uniqueFileName).normalize();
            if (!destinationPath.startsWith(this.rootLocation)) {
                throw new BadRequestException("Cannot store file outside current storage directory");
            }

            Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            String relativeStoragePath = String.valueOf(userId) + "/" + uniqueFileName;

            String determinedContentType = file.getContentType();
            if (!StringUtils.hasText(determinedContentType)) {
                determinedContentType = determineContentTypeByExtension(extension);
            }

            log.info("File stored successfully: {} (size: {} bytes, path: {})",
                    rawOriginalName, file.getSize(), relativeStoragePath);

            return new StoredFile(
                    uniqueFileName,
                    rawOriginalName,
                    determinedContentType,
                    file.getSize(),
                    relativeStoragePath,
                    destinationPath
            );
        } catch (IOException ex) {
            log.error("Failed to store file {}: {}", rawOriginalName, ex.getMessage(), ex);
            throw new RuntimeException("Failed to store uploaded file: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String storagePath) {
        try {
            Path filePath = resolvePath(storagePath);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File not found or cannot be read: " + storagePath);
            }
        } catch (MalformedURLException ex) {
            throw new ResourceNotFoundException("File not found with path: " + storagePath);
        }
    }

    @Override
    public boolean deleteFile(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return false;
        }

        try {
            Path filePath = resolvePath(storagePath);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Physical file deleted: {}", storagePath);
            }
            return deleted;
        } catch (IOException ex) {
            log.warn("Could not delete physical file at {}: {}", storagePath, ex.getMessage());
            return false;
        }
    }

    @Override
    public Path resolvePath(String storagePath) {
        return this.rootLocation.resolve(storagePath).normalize();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void validateMimeType(String contentType, String extension) {
        if (!StringUtils.hasText(contentType)) {
            return;
        }

        String lowerMime = contentType.toLowerCase(Locale.ROOT);
        switch (extension) {
            case "pdf":
                if (!lowerMime.contains("pdf") && !lowerMime.equals("application/octet-stream")) {
                    throw new BadRequestException("File content type '" + contentType + "' does not match PDF format");
                }
                break;
            case "docx":
                if (!lowerMime.contains("wordprocessingml") && !lowerMime.contains("officedocument") && !lowerMime.equals("application/octet-stream")) {
                    throw new BadRequestException("File content type '" + contentType + "' does not match DOCX format");
                }
                break;
            case "txt":
                if (!lowerMime.contains("text") && !lowerMime.equals("application/octet-stream")) {
                    throw new BadRequestException("File content type '" + contentType + "' does not match TXT format");
                }
                break;
            default:
                break;
        }
    }

    private String determineContentTypeByExtension(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
