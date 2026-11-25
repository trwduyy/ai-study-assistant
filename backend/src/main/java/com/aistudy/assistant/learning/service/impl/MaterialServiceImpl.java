package com.aistudy.assistant.learning.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.dto.MaterialChunkResponse;
import com.aistudy.assistant.learning.dto.MaterialDetailResponse;
import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.dto.UpdateMaterialRequest;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.mapper.LearningMapper;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.learning.service.FileStorageService;
import com.aistudy.assistant.learning.service.MaterialExtractionEvent;
import com.aistudy.assistant.learning.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Manages study material uploads, downloads, metadata updates, soft deletion, and async re-extraction.
 * Disk I/O executes strictly outside database transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final LearningMapper learningMapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public MaterialResponse uploadMaterial(Long courseId, MultipartFile file, Long topicId, String category) {
        Long currentUserId = SecurityUtils.currentUserId();

        // 1. Validate course ownership & optional topic inside a brief read-only transaction
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            findActiveCourseAndVerifyOwner(courseId, currentUserId);
            if (topicId != null && !topicRepository.existsByIdAndCourseId(topicId, courseId)) {
                throw new AppException(ErrorCode.TOPIC_NOT_FOUND);
            }
        });

        // 2. Store physical file and compute SHA-256 outside any database transaction
        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file, currentUserId);
        String normalizedCategory = normalizeCategory(category);

        try {
            // 3. Persist material record and publish post-commit extraction event
            Material saved = transactionTemplate.execute(status -> {
                Course course = findActiveCourseAndVerifyOwner(courseId, currentUserId);
                User owner = userRepository.findById(currentUserId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                Topic topic = resolveTopicInCourse(topicId, courseId);

                Material material = Material.builder()
                        .course(course)
                        .topic(topic)
                        .owner(owner)
                        .fileName(storedFile.sanitizedOriginalName())
                        .storedPath(storedFile.relativeStoredPath())
                        .mimeType(storedFile.mimeType())
                        .sizeBytes(storedFile.sizeBytes())
                        .category(normalizedCategory)
                        .status(MaterialStatus.UPLOADED)
                        .contentHash(storedFile.contentHash())
                        .build();

                material.setStatus(MaterialStatus.EXTRACTING);
                Material persisted = materialRepository.save(material);
                eventPublisher.publishEvent(new MaterialExtractionEvent(persisted.getId()));
                return persisted;
            });

            log.info("Material id={} uploaded for course id={} by user id={}", saved.getId(), courseId, currentUserId);
            return learningMapper.toMaterialResponse(saved);
        } catch (RuntimeException ex) {
            fileStorageService.deleteFile(storedFile.relativeStoredPath());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaterialResponse> getMaterials(
            Long courseId,
            Long topicId,
            String category,
            MaterialStatus status,
            Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        findActiveCourseAndVerifyOwner(courseId, currentUserId);

        String normalizedCategory = normalizeCategory(category);
        return materialRepository.findProjectedMaterials(
                courseId,
                currentUserId,
                topicId,
                normalizedCategory,
                status,
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialDetailResponse getMaterialDetail(Long materialId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);
        long chunkCount = materialChunkRepository.countByMaterialId(materialId);
        return learningMapper.toMaterialDetailResponse(material, chunkCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaterialChunkResponse> getMaterialContent(
            Long materialId,
            Integer pageFrom,
            Integer pageTo,
            Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);

        if (material.getStatus() == MaterialStatus.FAILED
                && ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.name().equals(material.getErrorCode())) {
            throw new AppException(ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED);
        }
        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
        }

        return materialChunkRepository.findProjectedByMaterialId(materialId, pageFrom, pageTo, pageable);
    }

    @Override
    public MaterialDownload downloadMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.currentUserId();

        MaterialFileRef ref = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);
            return new MaterialFileRef(material.getFileName(), material.getMimeType(), material.getStoredPath());
        });

        Resource resource = fileStorageService.loadFileAsResource(ref.storedPath());
        return new MaterialDownload(ref.fileName(), ref.mimeType(), resource);
    }

    @Override
    @Transactional
    public MaterialDetailResponse updateMaterial(Long materialId, UpdateMaterialRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);

        if (StringUtils.hasText(request.getFileName())) {
            String cleaned = request.getFileName().trim();
            if (cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
                throw new AppException(ErrorCode.INVALID_FILE_NAME);
            }
            material.setFileName(cleaned);
        }

        if (Boolean.TRUE.equals(request.getClearTopic())) {
            material.setTopic(null);
        } else if (request.getTopicId() != null) {
            Topic topic = resolveTopicInCourse(request.getTopicId(), material.getCourse().getId());
            material.setTopic(topic);
        }

        if (request.getCategory() != null) {
            material.setCategory(normalizeCategory(request.getCategory()));
        }

        Material updated = materialRepository.save(material);
        long chunkCount = materialChunkRepository.countByMaterialId(materialId);
        log.info("Updated material id={} by user id={}", materialId, currentUserId);
        return learningMapper.toMaterialDetailResponse(updated, chunkCount);
    }

    @Override
    @Transactional
    public void deleteMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);

        material.markDeleted();
        materialRepository.save(material);
        log.info("Soft-deleted material id={} by user id={}", materialId, currentUserId);
    }

    @Override
    public MaterialDetailResponse reextractMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.currentUserId();

        String storedPath = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);
            return material.getStoredPath();
        });

        Path resolved = fileStorageService.resolvePath(storedPath);
        if (!Files.exists(resolved) || !Files.isReadable(resolved)) {
            throw new AppException(ErrorCode.MATERIAL_NOT_FOUND, "Không tìm thấy tệp gốc để trích xuất lại.");
        }

        MaterialDetailResponse updated = transactionTemplate.execute(status -> {
            Material material = findActiveMaterialAndVerifyOwner(materialId, currentUserId);
            material.setStatus(MaterialStatus.EXTRACTING);
            material.setErrorCode(null);
            material.setErrorMessage(null);
            Material saved = materialRepository.save(material);
            eventPublisher.publishEvent(new MaterialExtractionEvent(saved.getId()));
            long chunkCount = materialChunkRepository.countByMaterialId(materialId);
            return learningMapper.toMaterialDetailResponse(saved, chunkCount);
        });

        log.info("Queued re-extraction for material id={} by user id={}", materialId, currentUserId);
        return updated;
    }

    private Course findActiveCourseAndVerifyOwner(Long courseId, Long userId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return course;
    }

    private Material findActiveMaterialAndVerifyOwner(Long materialId, Long userId) {
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));
        if (!material.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return material;
    }

    private Topic resolveTopicInCourse(Long topicId, Long courseId) {
        if (topicId == null) {
            return null;
        }
        Topic topic = topicRepository.findWithChapterCourseAndOwnerById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
        if (!topic.getChapter().getCourse().getId().equals(courseId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chủ đề không thuộc khóa học hiện tại.");
        }
        return topic;
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private record MaterialFileRef(String fileName, String mimeType, String storedPath) {
    }
}
