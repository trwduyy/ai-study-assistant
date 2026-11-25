package com.aistudy.assistant.classroom.mapper;

import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.dto.ClassMaterialResponse;
import com.aistudy.assistant.classroom.dto.ClassMemberResponse;
import com.aistudy.assistant.classroom.dto.ClassPostResponse;
import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.entity.ClassJoinRequest;
import com.aistudy.assistant.classroom.entity.ClassMaterial;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassPost;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.identity.entity.User;
import org.springframework.stereotype.Component;

/**
 * Maps {@code classroom} entities to REST response DTOs.
 */
@Component
public class ClassroomMapper {

    public ClassroomResponse toClassroomResponse(Classroom classroom, ClassRole myClassRole, long memberCount) {
        User owner = classroom.getOwner();
        return ClassroomResponse.builder()
                .id(classroom.getId())
                .code(classroom.getCode())
                .name(classroom.getName())
                .description(classroom.getDescription())
                .subject(classroom.getSubject())
                .avatarUrl(classroom.getAvatarUrl())
                .startAt(classroom.getStartAt())
                .endAt(classroom.getEndAt())
                .status(classroom.getStatus())
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .myClassRole(myClassRole)
                .memberCount(memberCount)
                .version(classroom.getVersion())
                .createdAt(classroom.getCreatedAt())
                .updatedAt(classroom.getUpdatedAt())
                .build();
    }

    public ClassMemberResponse toClassMemberResponse(ClassMember member) {
        User user = member.getUser();
        return ClassMemberResponse.builder()
                .id(member.getId())
                .classId(member.getClassroom().getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .classRole(member.getClassRole())
                .status(member.getStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    public ClassJoinRequestResponse toJoinRequestResponse(ClassJoinRequest request) {
        User student = request.getStudent();
        User decider = request.getDecidedBy();
        return ClassJoinRequestResponse.builder()
                .id(request.getId())
                .classId(request.getClassroom().getId())
                .className(request.getClassroom().getName())
                .studentId(student.getId())
                .studentName(student.getFullName())
                .studentEmail(student.getEmail())
                .status(request.getStatus())
                .decidedById(decider != null ? decider.getId() : null)
                .decidedByName(decider != null ? decider.getFullName() : null)
                .decidedAt(request.getDecidedAt())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    public ClassPostResponse toClassPostResponse(ClassPost post) {
        User author = post.getAuthor();
        return ClassPostResponse.builder()
                .id(post.getId())
                .classId(post.getClassroom().getId())
                .authorId(author.getId())
                .authorName(author.getFullName())
                .title(post.getTitle())
                .content(post.getContent())
                .pinned(post.getPinned())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    public ClassMaterialResponse toClassMaterialResponse(ClassMaterial material) {
        User uploader = material.getUploader();
        return ClassMaterialResponse.builder()
                .id(material.getId())
                .classId(material.getClassroom().getId())
                .uploaderId(uploader.getId())
                .uploaderName(uploader.getFullName())
                .fileName(material.getFileName())
                .mimeType(material.getMimeType())
                .sizeBytes(material.getSizeBytes())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
