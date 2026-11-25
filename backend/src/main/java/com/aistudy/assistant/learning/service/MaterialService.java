package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.learning.dto.MaterialChunkResponse;
import com.aistudy.assistant.learning.dto.MaterialDetailResponse;
import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.dto.UpdateMaterialRequest;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface MaterialService {

    MaterialResponse uploadMaterial(Long courseId, MultipartFile file, Long topicId, String category);

    Page<MaterialResponse> getMaterials(
            Long courseId,
            Long topicId,
            String category,
            MaterialStatus status,
            Pageable pageable
    );

    MaterialDetailResponse getMaterialDetail(Long materialId);

    Page<MaterialChunkResponse> getMaterialContent(
            Long materialId,
            Integer pageFrom,
            Integer pageTo,
            Pageable pageable
    );

    MaterialDownload downloadMaterial(Long materialId);

    MaterialDetailResponse updateMaterial(Long materialId, UpdateMaterialRequest request);

    void deleteMaterial(Long materialId);

    MaterialDetailResponse reextractMaterial(Long materialId);

    record MaterialDownload(String fileName, String mimeType, Resource resource) {
    }
}
