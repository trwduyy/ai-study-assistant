package com.aistudy.assistant.classroom.service;

import com.aistudy.assistant.classroom.dto.AddCoTeacherRequest;
import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.dto.ClassMaterialResponse;
import com.aistudy.assistant.classroom.dto.ClassMemberResponse;
import com.aistudy.assistant.classroom.dto.ClassPostResponse;
import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.dto.CreateClassPostRequest;
import com.aistudy.assistant.classroom.dto.CreateClassroomRequest;
import com.aistudy.assistant.classroom.dto.CreateJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.DecideJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.PinClassPostRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ClassroomService {

    ClassroomResponse createClassroom(CreateClassroomRequest request);

    Page<ClassroomResponse> getMyClassrooms(ClassroomStatus status, String keyword, Pageable pageable);

    ClassroomResponse getClassroomDetail(Long classId);

    ClassroomResponse updateClassroom(Long classId, UpdateClassroomRequest request, Integer ifMatchVersion);

    ClassroomResponse updateClassroomStatus(Long classId, UpdateClassroomStatusRequest request, Integer ifMatchVersion);

    ClassJoinRequestResponse requestJoin(CreateJoinRequestRequest request);

    Page<ClassJoinRequestResponse> getJoinRequests(Long classId, JoinRequestStatus status, Pageable pageable);

    ClassJoinRequestResponse decideJoinRequest(Long classId, Long reqId, DecideJoinRequestRequest request);

    Page<ClassMemberResponse> getMembers(Long classId, ClassRole role, Pageable pageable);

    ClassMemberResponse addCoTeacher(Long classId, AddCoTeacherRequest request);

    void removeMember(Long classId, Long targetUserId);

    void leaveClassroom(Long classId);

    Page<ClassPostResponse> getPosts(Long classId, Pageable pageable);

    ClassPostResponse createPost(Long classId, CreateClassPostRequest request);

    ClassPostResponse pinPost(Long classId, Long postId, PinClassPostRequest request);

    Page<ClassMaterialResponse> getMaterials(Long classId, Pageable pageable);

    ClassMaterialResponse uploadMaterial(Long classId, MultipartFile file);

    ClassMaterialDownload downloadMaterial(Long materialId);

    record ClassMaterialDownload(String fileName, String mimeType, Resource resource) {
    }
}
