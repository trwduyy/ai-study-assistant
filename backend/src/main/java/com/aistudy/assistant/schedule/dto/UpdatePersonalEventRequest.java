package com.aistudy.assistant.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePersonalEventRequest {

    @NotBlank(message = "Tiêu đề sự kiện cá nhân không được để trống.")
    @Size(max = 255, message = "Tiêu đề sự kiện tối đa 255 ký tự.")
    private String title;

    @Size(max = 5000, message = "Mô tả sự kiện tối đa 5000 ký tự.")
    private String description;

    @NotNull(message = "Thời gian bắt đầu là bắt buộc.")
    private Instant startAt;

    @NotNull(message = "Thời gian kết thúc là bắt buộc.")
    private Instant endAt;

    @Size(max = 64, message = "Múi giờ tối đa 64 ký tự.")
    private String timezone;

    @Size(max = 500, message = "Đường dẫn cuộc họp tối đa 500 ký tự.")
    private String meetingUrl;

    private Integer version;
}
