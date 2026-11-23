package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.material.MaterialDetailResponse;
import com.aistudy.assistant.dto.material.MaterialResponse;
import com.aistudy.assistant.dto.material.MaterialUploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialService {

    MaterialUploadResponse uploadMaterial(MultipartFile file, Long courseId, Long topicId);

    List<MaterialResponse> getMaterials(Long courseId, Long topicId);

    MaterialDetailResponse getMaterialDetail(Long materialId);

    Resource downloadMaterial(Long materialId);

    String getOriginalFileName(Long materialId);

    void deleteMaterial(Long materialId);
}
