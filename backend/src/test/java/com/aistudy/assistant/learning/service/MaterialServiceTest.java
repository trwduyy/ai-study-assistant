package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.mapper.LearningMapper;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.learning.service.impl.MaterialServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialChunkRepository materialChunkRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MaterialServiceImpl materialService;

    private User ownerUser;
    private User anotherUser;
    private Course ownedCourse;
    private Material ownedMaterial;

    @BeforeEach
    void setUp() {
        TransactionTemplate immediateTxTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(new SimpleTransactionStatus());
            }
        };

        materialService = new MaterialServiceImpl(
                materialRepository,
                materialChunkRepository,
                courseRepository,
                topicRepository,
                userRepository,
                fileStorageService,
                new LearningMapper(),
                immediateTxTemplate,
                eventPublisher
        );

        ownerUser = User.builder()
                .id(1L)
                .email("owner@actvn.edu.vn")
                .fullName("Owner")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        anotherUser = User.builder()
                .id(2L)
                .email("intruder@actvn.edu.vn")
                .fullName("Intruder")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        ownedCourse = Course.builder()
                .id(101L)
                .owner(ownerUser)
                .name("Mạng máy tính")
                .build();

        ownedMaterial = Material.builder()
                .id(401L)
                .course(ownedCourse)
                .owner(ownerUser)
                .fileName("Mang-may-tinh.pdf")
                .storedPath("1/uuid_Mang-may-tinh.pdf")
                .mimeType("application/pdf")
                .sizeBytes(1024L)
                .status(MaterialStatus.EXTRACTING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        authenticateAs(ownerUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return 403 NOT_RESOURCE_OWNER when another user accesses material detail or download")
    void accessMaterial_DifferentOwner_ThrowsForbidden() {
        authenticateAs(anotherUser);
        when(materialRepository.findByIdAndDeletedAtIsNull(401L)).thenReturn(Optional.of(ownedMaterial));

        AppException detailEx = assertThrows(AppException.class, () -> materialService.getMaterialDetail(401L));
        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, detailEx.getErrorCode());

        AppException downloadEx = assertThrows(AppException.class, () -> materialService.downloadMaterial(401L));
        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, downloadEx.getErrorCode());

        AppException deleteEx = assertThrows(AppException.class, () -> materialService.deleteMaterial(401L));
        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, deleteEx.getErrorCode());
    }

    @Test
    @DisplayName("Should upload material outside transaction, save EXTRACTING status, and publish extraction event")
    void uploadMaterial_Success_PublishesExtractionEvent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.pdf",
                "application/pdf",
                "PDF binary content".getBytes()
        );

        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));
        when(fileStorageService.storeFile(file, 1L)).thenReturn(
                new FileStorageService.StoredFile(
                        "lecture.pdf",
                        "application/pdf",
                        file.getSize(),
                        "1/uuid_lecture.pdf",
                        Path.of("uploads/1/uuid_lecture.pdf"),
                        "abc123hash"
                )
        );
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> {
            Material m = inv.getArgument(0);
            m.setId(402L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return m;
        });

        MaterialResponse response = materialService.uploadMaterial(101L, file, null, "slide");

        assertNotNull(response);
        assertEquals(402L, response.getId());
        assertEquals(MaterialStatus.EXTRACTING, response.getStatus());
        assertEquals("SLIDE", response.getCategory());

        ArgumentCaptor<MaterialExtractionEvent> eventCaptor = ArgumentCaptor.forClass(MaterialExtractionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(402L, eventCaptor.getValue().materialId());
    }

    @Test
    @DisplayName("Should return 422 TEXT_NOT_EXTRACTED when reading content of unextracted material")
    void getMaterialContent_NotExtracted_ThrowsUnprocessableEntity() {
        when(materialRepository.findByIdAndDeletedAtIsNull(401L)).thenReturn(Optional.of(ownedMaterial));

        AppException ex = assertThrows(
                AppException.class,
                () -> materialService.getMaterialContent(401L, null, null, PageRequest.of(0, 10))
        );
        assertEquals(ErrorCode.TEXT_NOT_EXTRACTED, ex.getErrorCode());
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
