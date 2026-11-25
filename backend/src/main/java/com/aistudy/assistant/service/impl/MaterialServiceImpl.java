package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.dto.material.MaterialDetailResponse;
import com.aistudy.assistant.dto.material.MaterialResponse;
import com.aistudy.assistant.dto.material.MaterialUploadResponse;
import com.aistudy.assistant.entity.Course;
import com.aistudy.assistant.entity.ExtractionStatus;
import com.aistudy.assistant.entity.Material;
import com.aistudy.assistant.entity.Topic;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.exception.UnprocessableEntityException;
import com.aistudy.assistant.repository.CourseRepository;
import com.aistudy.assistant.repository.MaterialRepository;
import com.aistudy.assistant.repository.TopicRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.service.FileStorageService;
import com.aistudy.assistant.service.MaterialService;
import com.aistudy.assistant.service.TextExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialRepository materialRepository;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;

    @Override
    @Transactional
    public MaterialUploadResponse uploadMaterial(MultipartFile file, Long courseId, Long topicId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Course course = courseRepository.findByIdAndUserId(courseId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        Topic topic = null;
        if (topicId != null) {
            topic = topicRepository.findByIdAndCourseId(topicId, courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));
        }

        FileStorageService.StoredFile storedFile = fileStorageService.storeFile(file, currentUserId);

        Material material = Material.builder()
                .course(course)
                .topic(topic)
                .user(currentUser)
                .fileName(storedFile.fileName())
                .originalName(storedFile.originalName())
                .fileType(storedFile.fileType())
                .fileSize(storedFile.fileSize())
                .storagePath(storedFile.storagePath())
                .extractionStatus(ExtractionStatus.PENDING)
                .build();

        try {
            String extractedText = textExtractionService.extractAndCleanText(
                    storedFile.absolutePath(),
                    storedFile.originalName()
            );

            material.setExtractedText(extractedText);
            material.setExtractionStatus(ExtractionStatus.SUCCESS);
            material.setErrorMessage(null);

            Material savedMaterial = materialRepository.save(material);
            log.info("Material {} uploaded and processed successfully for course: {}", savedMaterial.getId(), courseId);

            return MaterialUploadResponse.fromEntity(savedMaterial);

        } catch (UnprocessableEntityException ex) {
            material.setExtractedText(null);
            material.setExtractionStatus(ExtractionStatus.FAILED);
            material.setErrorMessage(ex.getMessage());
            materialRepository.save(material);

            log.warn("Text extraction failed for uploaded file {}: {}", storedFile.originalName(), ex.getMessage());
            throw ex;

        } catch (Exception ex) {
            material.setExtractedText(null);
            material.setExtractionStatus(ExtractionStatus.FAILED);
            material.setErrorMessage("Text extraction failed: " + ex.getMessage());
            materialRepository.save(material);

            log.error("Text extraction failed with unexpected error for {}: {}", storedFile.originalName(), ex.getMessage(), ex);
            throw new BadRequestException("Failed to process document text: " + ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialResponse> getMaterials(Long courseId, Long topicId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Material> materials;

        if (courseId != null && topicId != null) {
            materials = materialRepository.findByCourseIdAndTopicIdAndUserIdOrderByCreatedAtDesc(courseId, topicId, currentUserId);
        } else if (courseId != null) {
            materials = materialRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, currentUserId);
        } else {
            materials = materialRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
        }

        return materials.stream()
                .map(MaterialResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialDetailResponse getMaterialDetail(Long materialId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Material material = findMaterialAndValidateOwnership(materialId, currentUserId);

        return MaterialDetailResponse.fromEntity(material);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Material material = findMaterialAndValidateOwnership(materialId, currentUserId);

        return fileStorageService.loadFileAsResource(material.getStoragePath());
    }

    @Override
    @Transactional(readOnly = true)
    public String getOriginalFileName(Long materialId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Material material = findMaterialAndValidateOwnership(materialId, currentUserId);
        return material.getOriginalName();
    }

    @Override
    @Transactional
    public void deleteMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Material material = findMaterialAndValidateOwnership(materialId, currentUserId);

        fileStorageService.deleteFile(material.getStoragePath());
        materialRepository.delete(material);

        log.info("Material {} deleted successfully by user: {}", materialId, currentUserId);
    }

    private Material findMaterialAndValidateOwnership(Long materialId, Long userId) {
        return materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Material", "id", materialId));
    }
}
