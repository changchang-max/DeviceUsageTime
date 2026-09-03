package top.primordialcode.backend.vo.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.time.Instant;
import java.util.List;

/**
 * WebSocket 实时数据推送消息(服务端 → 客户端)
 *
 * 消息格式:
 * {
 *   "type": "realtime_update",
 *   "userId": "12345",
 *   "timestamp": "2026-07-12T10:30:45Z",
 *   "data": {
 *     "applications": [ ... ],
 *     "statistics": { ... }
 *   }
 * }
 *
 * 推送时机: 客户端每次上传数据后立即推送给所有订阅该用户的查看者
 */
@Data
@NoArgsConstructor
public class WsRealtimeUpdateMessage {

    /**
     * 消息类型
     */
    private String type = "realtime_update";

    /**
     * 数据所属用户标识(本系统中为用户邮箱)
     */
    private String userId;

    /**
     * 数据时间戳
     */
    private Instant timestamp;

    /**
     * 实时数据内容(完整快照: 所有应用的累计数据 + 统计数据)
     */
    private WsRealtimeData data;

    public WsRealtimeUpdateMessage(String userId, Instant timestamp, WsRealtimeData data) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.data = data;
    }

    /**
     * 实时数据内容体
     */
    @Data
    @NoArgsConstructor
    public static class WsRealtimeData {

        /**
         * 应用使用情况列表
         */
        private List<ApplicationDTO> applications;

        /**
         * 统计数据
         */
        private StatisticsDTO statistics;

        public WsRealtimeData(List<ApplicationDTO> applications, StatisticsDTO statistics) {
            this.applications = applications;
            this.statistics = statistics;
        }
    }
}
