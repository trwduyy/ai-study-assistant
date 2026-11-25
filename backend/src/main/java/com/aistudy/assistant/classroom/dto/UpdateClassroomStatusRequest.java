package com.aistudy.assistant.classroom.dto;

import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for changing a classroom's lifecycle status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassroomStatusRequest {

    @NotNull(message = "Trạng thái lớp học không được để trống.")
    private ClassroomStatus status;

    private Integer version;
}
