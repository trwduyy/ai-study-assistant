package com.aistudy.assistant.admin.controller;

import com.aistudy.assistant.admin.service.AdminService;
import com.aistudy.assistant.common.exception.GlobalExceptionHandler;
import com.aistudy.assistant.identity.security.CustomAccessDeniedHandler;
import com.aistudy.assistant.identity.security.CustomUserDetailsService;
import com.aistudy.assistant.identity.security.JwtAuthenticationEntryPoint;
import com.aistudy.assistant.identity.security.JwtTokenProvider;
import com.aistudy.assistant.identity.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        CustomAccessDeniedHandler.class,
        JwtAuthenticationEntryPoint.class
})
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("STUDENT calling /api/v1/admin/** is rejected with 403 FORBIDDEN")
    void studentCallingAdminEndpoints_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/admin/classes/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    @DisplayName("TEACHER calling /api/v1/admin/** is rejected with 403 FORBIDDEN")
    void teacherCallingAdminEndpoints_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/v1/admin/reports/301")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN calling /api/v1/admin/settings succeeds with 200 OK")
    void adminCallingAdminSettings_Returns200Ok() throws Exception {
        when(adminService.getSettings()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
