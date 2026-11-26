package com.aistudy.assistant.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    @NotBlank(message = "Loại báo cáo không được để trống.")
    @Size(max = 64, message = "Loại báo cáo tối đa 64 ký tự.")
    private String type;

    @NotBlank(message = "Đối tượng báo cáo không được để trống.")
    @Size(max = 64, message = "Loại đối tượng tối đa 64 ký tự.")
    private String targetType;

    @NotNull(message = "Mã đối tượng báo cáo không được để trống.")
    private Long targetId;

    @NotBlank(message = "Nội dung chi tiết báo cáo không được để trống.")
    @Size(max = 4000, message = "Nội dung báo cáo tối đa 4000 ký tự.")
    private String detail;
}
