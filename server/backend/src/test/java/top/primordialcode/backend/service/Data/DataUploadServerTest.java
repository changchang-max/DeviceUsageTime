package top.primordialcode.backend.service.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.exception.ServiceUnavailableException;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.UploadCredential.UploadCredentialServer;
import top.primordialcode.backend.utils.DataDateUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据上传服务测试
 *
 * 上传链路已改为"快收 + 异步落"：请求线程只等 Redis 单次脚本写入，
 * 归档与推送通过 RealtimeUpdateEvent 交给异步消费者。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据上传服务测试")
class DataUploadServerTest {

    @Mock
    private RedisDataUploadServer redisDataUploadServer;

    @Mock
    private UploadCredentialServer uploadCredentialServer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DataUploadServer dataUploadServer;

    private DataUploadMainDTO testData;
    private String email;
    private String credential;

    /** 上传时间戳(取当前时间，保证归属日期=今天) */
    private Instant timestamp;

    /** 由 timestamp 换算出的数据归属日期 */
    private LocalDate date;

    @BeforeEach
    void setUp() {
        credential = "upload-credential-001";
        email = "test@example.com";
        timestamp = Instant.now();
        date = DataDateUtil.toDataDate(timestamp);

        testData = new DataUploadMainDTO();
        testData.setTimestamp(timestamp);
        testData.setUploadCredential(credential);

        ApplicationDTO app = new ApplicationDTO();
        app.setName("Chrome");
        app.setWindowTitle("Google");
        app.setDuration(3600L);
        app.setIsActive(true);
        testData.setApplications(Arrays.asList(app));

        StatisticsDTO stats = new StatisticsDTO();
        stats.setKeyboardCount(1250L);
        stats.setMouseClickCount(856L);
        stats.setMouseDistance(23.01);
        testData.setStatistics(stats);
    }

    @Test
    @DisplayName("成功接收数据: 一次原子写入快照并发布事件")
    void testReceiveSuccess() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);

        dataUploadServer.receive(testData);

        // 一次写入合并了 applications / statistics / otherData
        ArgumentCaptor<RedisSaveOtherDataDTO> otherCaptor =
                ArgumentCaptor.forClass(RedisSaveOtherDataDTO.class);
        verify(redisDataUploadServer, times(1)).writeSnapshot(
                eq(email), eq(date), anyList(), any(StatisticsDTO.class), otherCaptor.capture());

        assertEquals(email, otherCaptor.getValue().getUserEmail());
        assertEquals(timestamp, otherCaptor.getValue().getTimestamp());

        // 归档与推送不在请求线程内，只发布事件
        verify(eventPublisher, times(1)).publishEvent(any(RealtimeUpdateEvent.class));
    }

    @Test
    @DisplayName("上传凭证无效时抛出IllegalArgumentException且不写Redis")
    void testReceiveInvalidCredential() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(null);
        testData.setUserEmail(null);

        assertThrows(IllegalArgumentException.class, () -> dataUploadServer.receive(testData));

        verify(redisDataUploadServer, never())
                .writeSnapshot(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("兼容旧客户端: 无凭证时回退到请求体 userEmail")
    void testReceiveLegacyUserEmailFallback() {
        testData.setUploadCredential(null);
        testData.setUserEmail(email);

        dataUploadServer.receive(testData);

        verify(redisDataUploadServer, times(1))
                .writeSnapshot(eq(email), eq(date), anyList(), any(StatisticsDTO.class), any());
    }

    @Test
    @DisplayName("timestamp为null抛出IllegalArgumentException")
    void testReceiveWithNullTimestamp() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);
        testData.setTimestamp(null);

        assertThrows(IllegalArgumentException.class, () -> dataUploadServer.receive(testData));

        verify(redisDataUploadServer, never())
                .writeSnapshot(anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("applications为空列表时仍然调用写入(用于把快照应用标记为已关闭)")
    void testReceiveWithEmptyApplications() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);
        testData.setApplications(List.of());

        dataUploadServer.receive(testData);

        verify(redisDataUploadServer, times(1))
                .writeSnapshot(eq(email), eq(date), eq(List.of()), any(), any());
    }

    @Test
    @DisplayName("statistics为null时仍然写入(脚本内跳过该 key)")
    void testReceiveWithNullStatistics() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);
        testData.setStatistics(null);

        dataUploadServer.receive(testData);

        verify(redisDataUploadServer, times(1))
                .writeSnapshot(eq(email), eq(date), anyList(), isNull(), any());
    }

    @Test
    @DisplayName("Redis写入失败抛出ServiceUnavailableException(交由全局处理器映射503)")
    void testReceiveWithRedisFailure() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);
        doThrow(new ServiceUnavailableException("实时数据写入Redis失败"))
                .when(redisDataUploadServer)
                .writeSnapshot(anyString(), any(), any(), any(), any());

        assertThrows(ServiceUnavailableException.class, () -> dataUploadServer.receive(testData));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("非今日数据也会发布事件(归档)；是否推送由消费者按日期判断")
    void testReceiveOtherDateStillPublishesEvent() {
        when(uploadCredentialServer.resolveEmail(credential)).thenReturn(email);
        // 时间戳向前平移48小时，归属日期恒等于"今天-2天"
        Instant otherDateTimestamp = timestamp.minus(Duration.ofDays(2));
        LocalDate otherDate = DataDateUtil.toDataDate(otherDateTimestamp);
        testData.setTimestamp(otherDateTimestamp);

        dataUploadServer.receive(testData);

        verify(redisDataUploadServer, times(1))
                .writeSnapshot(eq(email), eq(otherDate), anyList(), any(), any());
        verify(eventPublisher, times(1)).publishEvent(any(RealtimeUpdateEvent.class));
    }
}