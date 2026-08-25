package top.primordialcode.backend.controller.api.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.time.Instant;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("数据上传接口测试")
class DataUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String validToken;
    private DataUploadMainDTO validData;

    @BeforeEach
    void setUp() {
        validToken = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwicm9sZSI6IlJPTEVfVVNFUiIsImlhdCI6MTcyNDU4MDAwMCwiZXhwIjo5OTk5OTk5OTk5fQ.test";

        validData = new DataUploadMainDTO();
        validData.setUserEmail("test@example.com");
        validData.setTimestamp(Instant.parse("2026-07-12T10:30:45Z"));

        ApplicationDTO app1 = new ApplicationDTO();
        app1.setName("Chrome");
        app1.setWindowTitle("Google搜索");
        app1.setDuration(3600L);
        app1.setIsActive(true);

        ApplicationDTO app2 = new ApplicationDTO();
        app2.setName("VSCode");
        app2.setWindowTitle("main.py");
        app2.setDuration(1800L);
        app2.setIsActive(false);

        validData.setApplications(Arrays.asList(app1, app2));

        StatisticsDTO stats = new StatisticsDTO();
        stats.setKeyboardCount(1250L);
        stats.setMouseClickCount(856L);
        stats.setMouseDistance(23.01);

        validData.setStatistics(stats);
    }

    @Test
    @DisplayName("成功上传数据 - 返回200")
    void testUploadSuccess() throws Exception {
        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据上传成功"));
    }

    @Test
    @DisplayName("缺少Authorization头 - 返回401")
    void testUploadWithoutToken() throws Exception {
        mockMvc.perform(post("/api/data/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token格式错误 - 返回401")
    void testUploadWithInvalidTokenFormat() throws Exception {
        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", "InvalidToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("请求体为空 - 返回400")
    void testUploadWithEmptyBody() throws Exception {
        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("缺少必填字段timestamp - 返回400")
    void testUploadWithMissingTimestamp() throws Exception {
        validData.setTimestamp(null);

        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("applications为空列表 - 成功")
    void testUploadWithEmptyApplications() throws Exception {
        validData.setApplications(Arrays.asList());

        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("statistics为null - 成功")
    void testUploadWithNullStatistics() throws Exception {
        validData.setStatistics(null);

        mockMvc.perform(post("/api/data/upload")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
