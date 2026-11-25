package com.aistudy.assistant.classroom.dto;

import com.aistudy.assistant.classroom.entity.JoinRequestDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for approving or rejecting a pending classroom join request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideJoinRequestRequest {

    @NotNull(message = "Quyết định duyệt không được để trống.")
    private JoinRequestDecision decision;

    @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự.")
    private String rejectionReason;
}
