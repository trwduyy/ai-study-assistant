package com.aistudy.assistant.learning.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validates, hashes (SHA-256), sanitizes, and stores uploaded study files outside database transactions.
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt");
    private static final int BUFFER_SIZE = 8192;

    private final Path rootLocation;

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("File upload storage initialized at: {}", rootLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize upload storage directory: " + rootLocation, ex);
        }
    }

    @Override
    public StoredFile storeFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String rawName = file.getOriginalFilename();
        if (!StringUtils.hasText(rawName)) {
            throw new AppException(ErrorCode.INVALID_FILE_NAME);
        }

        String normalizedRawName = StringUtils.cleanPath(rawName);
        if (normalizedRawName.contains("..") || normalizedRawName.contains("/") || normalizedRawName.contains("\\")) {
            throw new AppException(ErrorCode.INVALID_FILE_NAME);
        }

        String extension = extractExtension(normalizedRawName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException(
                    ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Chỉ hỗ trợ định dạng tệp .pdf, .docx hoặc .txt."
            );
        }

        String resolvedMimeType = validateAndResolveMimeType(file.getContentType(), extension);
        String sanitizedName = sanitizeFilename(normalizedRawName, extension);
        String storedFileName = UUID.randomUUID() + "_" + sanitizedName;

        try {
            Path userDir = rootLocation.resolve(String.valueOf(userId)).normalize();
            if (!userDir.startsWith(rootLocation)) {
                throw new AppException(ErrorCode.INVALID_FILE_NAME);
            }
            Files.createDirectories(userDir);

            Path destinationPath = userDir.resolve(storedFileName).normalize();
            if (!destinationPath.startsWith(userDir)) {
                throw new AppException(ErrorCode.INVALID_FILE_NAME);
            }

            String contentHash = writeAndHashFile(file, destinationPath);
            String relativePath = userId + "/" + storedFileName;

            log.info("Stored material file for userId={} at {} ({} bytes)", userId, relativePath, file.getSize());
            return new StoredFile(
                    sanitizedName,
                    resolvedMimeType,
                    file.getSize(),
                    relativePath,
                    destinationPath,
                    contentHash
            );
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to store uploaded file for userId={}", userId, ex);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể lưu tệp tải lên.");
        }
    }

    @Override
    public Resource loadFileAsResource(String storedPath) {
        try {
            Path resolved = resolvePath(storedPath);
            Resource resource = new UrlResource(resolved.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AppException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy tệp tài liệu trên hệ thống lưu trữ.");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new AppException(ErrorCode.MATERIAL_NOT_FOUND, "Đường dẫn tệp tài liệu không hợp lệ.");
        }
    }

    @Override
    public boolean deleteFile(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return false;
        }
        try {
            Path resolved = resolvePath(storedPath);
            return Files.deleteIfExists(resolved);
        } catch (Exception ex) {
            log.warn("Could not delete file at {}: {}", storedPath, ex.getMessage());
            return false;
        }
    }

    @Override
    public Path resolvePath(String storedPath) {
        Path resolved = rootLocation.resolve(storedPath).normalize();
        if (!resolved.startsWith(rootLocation)) {
            throw new AppException(ErrorCode.INVALID_FILE_NAME);
        }
        return resolved;
    }

    private String writeAndHashFile(MultipartFile file, Path destinationPath) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestStream = new DigestInputStream(inputStream, digest);
             OutputStream outputStream = Files.newOutputStream(destinationPath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = digestStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hex = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(String filename, String extension) {
        int lastDot = filename.lastIndexOf('.');
        String baseName = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        String cleanBase = baseName
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(cleanBase)) {
            cleanBase = "document";
        }
        if (cleanBase.length() > 160) {
            cleanBase = cleanBase.substring(0, 160);
        }
        return cleanBase + "." + extension;
    }

    private String validateAndResolveMimeType(String contentType, String extension) {
        String defaultMime = defaultMimeTypeForExtension(extension);
        if (!StringUtils.hasText(contentType)) {
            return defaultMime;
        }
        String normalizedMime = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIdx = normalizedMime.indexOf(';');
        if (semicolonIdx > 0) {
            normalizedMime = normalizedMime.substring(0, semicolonIdx).trim();
        }

        boolean matches = switch (extension) {
            case "pdf" -> normalizedMime.equals("application/pdf")
                    || normalizedMime.equals("application/x-pdf")
                    || normalizedMime.equals("application/octet-stream");
            case "docx" -> normalizedMime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || normalizedMime.equals("application/octet-stream");
            case "txt" -> normalizedMime.startsWith("text/")
                    || normalizedMime.equals("application/octet-stream");
            default -> false;
        };

        if (!matches) {
            throw new AppException(
                    ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "MIME type '" + normalizedMime + "' không khớp với định dạng tệp ." + extension + "."
            );
        }
        return normalizedMime.equals("application/octet-stream") ? defaultMime : normalizedMime;
    }

    private String defaultMimeTypeForExtension(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
