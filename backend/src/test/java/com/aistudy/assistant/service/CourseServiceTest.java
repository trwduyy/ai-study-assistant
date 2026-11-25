package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.course.CourseDetailResponse;
import com.aistudy.assistant.dto.course.CourseResponse;
import com.aistudy.assistant.dto.course.CreateCourseRequest;
import com.aistudy.assistant.dto.course.CreateTopicRequest;
import com.aistudy.assistant.dto.course.TopicResponse;
import com.aistudy.assistant.dto.course.UpdateCourseRequest;
import com.aistudy.assistant.entity.Course;
import com.aistudy.assistant.entity.Topic;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.CourseRepository;
import com.aistudy.assistant.repository.MaterialRepository;
import com.aistudy.assistant.repository.TopicRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CourseServiceImpl courseService;

    private User mockUser;
    private Course mockCourse;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("student01@example.com")
                .fullName("Nguyen Van A")
                .passwordHash("hashedPass")
                .build();

        UserPrincipal principal = UserPrincipal.create(mockUser);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockCourse = Course.builder()
                .id(10L)
                .user(mockUser)
                .title("Database Systems")
                .description("Relational database architecture")
                .topics(new ArrayList<>())
                .materials(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should successfully create a course for authenticated user")
    void createCourse_Success() {
        CreateCourseRequest request = CreateCourseRequest.builder()
                .title("Operating Systems")
                .description("Process and memory management")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertEquals("Operating Systems", response.getTitle());
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("Should retrieve user courses with batch topic and material counts without N+1 queries")
    void getUserCourses_BatchCounts_Success() {
        when(courseRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(mockCourse));
        when(courseRepository.countTopicsByCourseIds(List.of(10L))).thenReturn(List.of(
                new CourseRepository.CourseCountProjection() {
                    @Override
                    public Long getCourseId() {
                        return 10L;
                    }

                    @Override
                    public Long getTotal() {
                        return 3L;
                    }
                }
        ));
        when(courseRepository.countMaterialsByCourseIds(List.of(10L))).thenReturn(List.of(
                new CourseRepository.CourseCountProjection() {
                    @Override
                    public Long getCourseId() {
                        return 10L;
                    }

                    @Override
                    public Long getTotal() {
                        return 7L;
                    }
                }
        ));

        List<CourseResponse> responses = courseService.getUserCourses();

        assertEquals(1, responses.size());
        assertEquals(3L, responses.get(0).getTopicCount());
        assertEquals(7L, responses.get(0).getMaterialCount());
        verify(courseRepository, never()).countTopicsByCourseId(anyLong());
        verify(courseRepository, never()).countMaterialsByCourseId(anyLong());
    }

    @Test
    @DisplayName("Should retrieve course details when authenticated user owns the course")
    void getCourseDetail_OwnershipValidated_Success() {
        when(courseRepository.findWithTopicsByIdAndUserId(10L, 1L)).thenReturn(Optional.of(mockCourse));

        CourseDetailResponse response = courseService.getCourseDetail(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Database Systems", response.getTitle());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when course does not belong to user")
    void getCourseDetail_UnauthorizedCourse_ThrowsNotFound() {
        when(courseRepository.findWithTopicsByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> courseService.getCourseDetail(99L));
    }

    @Test
    @DisplayName("Should successfully update course owned by user")
    void updateCourse_Success() {
        UpdateCourseRequest request = UpdateCourseRequest.builder()
                .title("Advanced Database Systems")
                .description("NoSQL and distributed databases")
                .build();

        when(courseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(mockCourse));
        when(courseRepository.save(any(Course.class))).thenReturn(mockCourse);
        when(courseRepository.countTopicsByCourseId(10L)).thenReturn(2L);
        when(courseRepository.countMaterialsByCourseId(10L)).thenReturn(5L);

        CourseResponse response = courseService.updateCourse(10L, request);

        assertNotNull(response);
        assertEquals("Advanced Database Systems", mockCourse.getTitle());
        assertEquals(2L, response.getTopicCount());
        assertEquals(5L, response.getMaterialCount());
    }

    @Test
    @DisplayName("Should create topic within course owned by user")
    void createTopic_Success() {
        CreateTopicRequest request = CreateTopicRequest.builder()
                .title("Indexing and B-Trees")
                .orderIndex(1)
                .build();

        when(courseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(mockCourse));
        when(topicRepository.save(any(Topic.class))).thenAnswer(invocation -> {
            Topic saved = invocation.getArgument(0);
            saved.setId(101L);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        TopicResponse response = courseService.createTopic(10L, request);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("Indexing and B-Trees", response.getTitle());
        assertEquals(1, response.getOrderIndex());
        assertEquals(10L, response.getCourseId());
    }
}
