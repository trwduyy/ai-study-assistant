package com.aistudy.assistant.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateSystemSettingsRequest {

    @Valid
    @NotEmpty(message = "Danh sách cấu hình cần cập nhật không được để trống.")
    private List<SettingUpdateItem> settings;

    @Getter
    @Setter
    public static class SettingUpdateItem {

        @NotBlank(message = "settingKey không được để trống.")
        private String settingKey;

        @NotNull(message = "settingValue không được để trống.")
        private String settingValue;
    }
}
