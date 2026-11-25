package com.aistudy.assistant.classroom.service;

import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomAccessServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassMemberRepository classMemberRepository;

    @InjectMocks
    private ClassroomAccessService classroomAccessService;

    private Classroom classroom;
    private User ownerTeacher;
    private User coTeacher;
    private User student;

    @BeforeEach
    void setUp() {
        ownerTeacher = User.builder()
                .id(1L)
                .email("owner@actvn.edu.vn")
                .fullName("Owner Teacher")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        coTeacher = User.builder()
                .id(2L)
                .email("coteacher@actvn.edu.vn")
                .fullName("Co Teacher")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        student = User.builder()
                .id(3L)
                .email("student@actvn.edu.vn")
                .fullName("Student")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        classroom = Classroom.builder()
                .id(10L)
                .code("KMA7N9P4")
                .name("Mạng máy tính")
                .status(ClassroomStatus.OPEN)
                .owner(ownerTeacher)
                .build();
    }

    @Test
    @DisplayName("requireActiveMember throws 404 CLASS_NOT_FOUND when classroom does not exist")
    void requireActiveMember_ClassNotFound_ThrowsNotFound() {
        when(classroomRepository.existsById(999L)).thenReturn(false);

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireActiveMember(999L, 3L)
        );
        assertEquals(ErrorCode.CLASS_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("requireActiveMember throws 403 NOT_CLASS_MEMBER for outsider, LEFT, and REMOVED members")
    void requireActiveMember_NonActiveOrOutsider_ThrowsNotClassMember() {
        when(classroomRepository.existsById(10L)).thenReturn(true);

        when(classMemberRepository.findByClassroomIdAndUserId(10L, 99L)).thenReturn(Optional.empty());
        AppException outsiderEx = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireActiveMember(10L, 99L)
        );
        assertEquals(ErrorCode.NOT_CLASS_MEMBER, outsiderEx.getErrorCode());

        ClassMember leftMember = ClassMember.builder()
                .id(101L)
                .classroom(classroom)
                .user(student)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.LEFT)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 3L)).thenReturn(Optional.of(leftMember));
        AppException leftEx = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireActiveMember(10L, 3L)
        );
        assertEquals(ErrorCode.NOT_CLASS_MEMBER, leftEx.getErrorCode());

        ClassMember removedMember = ClassMember.builder()
                .id(102L)
                .classroom(classroom)
                .user(student)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.REMOVED)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 4L)).thenReturn(Optional.of(removedMember));
        AppException removedEx = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireActiveMember(10L, 4L)
        );
        assertEquals(ErrorCode.NOT_CLASS_MEMBER, removedEx.getErrorCode());
    }

    @Test
    @DisplayName("requireTeacherInClass rejects active student with 403 FORBIDDEN and allows co-teacher")
    void requireTeacherInClass_StudentRejectedCoTeacherAllowed() {
        when(classroomRepository.existsById(10L)).thenReturn(true);

        ClassMember activeStudent = ClassMember.builder()
                .id(103L)
                .classroom(classroom)
                .user(student)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.ACTIVE)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 3L)).thenReturn(Optional.of(activeStudent));

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireTeacherInClass(10L, 3L)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());

        ClassMember activeCoTeacher = ClassMember.builder()
                .id(104L)
                .classroom(classroom)
                .user(coTeacher)
                .classRole(ClassRole.CO_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 2L)).thenReturn(Optional.of(activeCoTeacher));

        ClassMember result = classroomAccessService.requireTeacherInClass(10L, 2L);
        assertEquals(ClassRole.CO_TEACHER, result.getClassRole());
    }

    @Test
    @DisplayName("requireOwnerTeacher rejects co-teacher with 403 FORBIDDEN and allows owner teacher")
    void requireOwnerTeacher_CoTeacherRejectedOwnerAllowed() {
        when(classroomRepository.existsById(10L)).thenReturn(true);

        ClassMember activeCoTeacher = ClassMember.builder()
                .id(104L)
                .classroom(classroom)
                .user(coTeacher)
                .classRole(ClassRole.CO_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 2L)).thenReturn(Optional.of(activeCoTeacher));

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomAccessService.requireOwnerTeacher(10L, 2L)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());

        ClassMember activeOwner = ClassMember.builder()
                .id(100L)
                .classroom(classroom)
                .user(ownerTeacher)
                .classRole(ClassRole.OWNER_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .build();
        when(classMemberRepository.findByClassroomIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeOwner));

        ClassMember result = classroomAccessService.requireOwnerTeacher(10L, 1L);
        assertEquals(ClassRole.OWNER_TEACHER, result.getClassRole());
    }
}
