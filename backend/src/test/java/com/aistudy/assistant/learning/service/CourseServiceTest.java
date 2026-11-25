package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.dto.CourseDetailResponse;
import com.aistudy.assistant.learning.dto.CourseResponse;
import com.aistudy.assistant.learning.dto.UpdateCourseRequest;
import com.aistudy.assistant.learning.entity.Chapter;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.mapper.LearningMapper;
import com.aistudy.assistant.learning.repository.ChapterRepository;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.learning.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private LearningMapper learningMapper = new LearningMapper();

    @InjectMocks
    private CourseServiceImpl courseService;

    private User ownerUser;
    private User anotherUser;
    private Course ownedCourse;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder()
                .id(1L)
                .email("owner@actvn.edu.vn")
                .fullName("Owner Student")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        anotherUser = User.builder()
                .id(2L)
                .email("other@actvn.edu.vn")
                .fullName("Other Student")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        ownedCourse = Course.builder()
                .id(101L)
                .owner(ownerUser)
                .name("Mạng máy tính")
                .description("TCP/IP")
                .version(1)
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
    @DisplayName("Should return 403 NOT_RESOURCE_OWNER when another user accesses a course")
    void getCourseDetail_DifferentOwner_ThrowsForbidden() {
        authenticateAs(anotherUser);
        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));

        AppException ex = assertThrows(AppException.class, () -> courseService.getCourseDetail(101L));
        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should return 409 VERSION_CONFLICT when updating course with stale version")
    void updateCourse_StaleVersion_ThrowsConflict() {
        UpdateCourseRequest request = UpdateCourseRequest.builder()
                .name("Mạng máy tính V2")
                .description("Updated")
                .version(0)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));

        AppException ex = assertThrows(AppException.class, () -> courseService.updateCourse(101L, request, null));
        assertEquals(ErrorCode.VERSION_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should update course successfully when version matches")
    void updateCourse_MatchingVersion_Success() {
        UpdateCourseRequest request = UpdateCourseRequest.builder()
                .name("Mạng máy tính nâng cao")
                .description("Updated")
                .version(1)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));
        when(courseRepository.saveAndFlush(any(Course.class))).thenReturn(ownedCourse);
        when(chapterRepository.countByCourseId(101L)).thenReturn(2L);
        when(materialRepository.countByCourseIdAndDeletedAtIsNull(101L)).thenReturn(5L);

        CourseResponse response = courseService.updateCourse(101L, request, null);

        assertEquals("Mạng máy tính nâng cao", response.getName());
        assertEquals(2L, response.getChapterCount());
        assertEquals(5L, response.getMaterialCount());
    }

    @Test
    @DisplayName("Should load course detail with ordered chapters and batch-loaded topics")
    void getCourseDetail_BatchLoadsTopics_Success() {
        Chapter ch1 = Chapter.builder().id(201L).course(ownedCourse).title("Chương 1").position(1).build();
        Topic t1 = Topic.builder().id(301L).chapter(ch1).title("TCP").position(1).build();

        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));
        when(chapterRepository.findByCourseIdOrdered(101L)).thenReturn(List.of(ch1));
        when(topicRepository.findByChapterIdsOrdered(List.of(201L))).thenReturn(List.of(t1));

        CourseDetailResponse detail = courseService.getCourseDetail(101L);

        assertEquals(1, detail.getChapters().size());
        assertEquals(1, detail.getChapters().get(0).getTopics().size());
        assertEquals("TCP", detail.getChapters().get(0).getTopics().get(0).getTitle());
    }

    @Test
    @DisplayName("Should soft-delete course and cascade soft-delete to its materials")
    void deleteCourse_SoftDeletesCourseAndMaterials() {
        when(courseRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(ownedCourse));

        courseService.deleteCourse(101L);

        assertNotNull(ownedCourse.getDeletedAt());
        verify(courseRepository).save(ownedCourse);
        verify(materialRepository).softDeleteByCourseId(eq(101L), any(Instant.class));
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
