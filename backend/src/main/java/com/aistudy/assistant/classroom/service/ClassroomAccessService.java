package com.aistudy.assistant.classroom.service;

import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared classroom authorization helper enforcing active membership and class-level roles on the backend.
 * Reusable across {@code classroom}, {@code assignment}, and {@code chat} modules.
 */
@Service
@RequiredArgsConstructor
public class ClassroomAccessService {

    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository classMemberRepository;

    @Transactional(readOnly = true)
    public Classroom requireClassroomExists(Long classId) {
        return classroomRepository.findWithOwnerById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ClassMember requireActiveMember(Long classId, Long userId) {
        if (!classroomRepository.existsById(classId)) {
            throw new AppException(ErrorCode.CLASS_NOT_FOUND);
        }
        ClassMember member = classMemberRepository.findByClassroomIdAndUserId(classId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_CLASS_MEMBER));
        if (member.getStatus() != ClassMemberStatus.ACTIVE) {
            throw new AppException(ErrorCode.NOT_CLASS_MEMBER);
        }
        return member;
    }

    @Transactional(readOnly = true)
    public ClassMember requireTeacherInClass(Long classId, Long userId) {
        ClassMember member = requireActiveMember(classId, userId);
        if (member.getClassRole() != ClassRole.OWNER_TEACHER && member.getClassRole() != ClassRole.CO_TEACHER) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ giảng viên của lớp học mới có quyền thực hiện thao tác này.");
        }
        return member;
    }

    @Transactional(readOnly = true)
    public ClassMember requireOwnerTeacher(Long classId, Long userId) {
        ClassMember member = requireActiveMember(classId, userId);
        if (member.getClassRole() != ClassRole.OWNER_TEACHER) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ giảng viên chủ nhiệm lớp mới có quyền thực hiện thao tác này.");
        }
        return member;
    }
}
