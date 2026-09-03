package top.primordialcode.backend.vo.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * WebSocket 心跳响应消息(服务端 → 客户端)
 *
 * 消息格式:
 * {
 *   "type": "pong",
 *   "timestamp": "2026-07-12T10:30:45Z"
 * }
 */
@Data
@NoArgsConstructor
public class WsPongMessage {

    /**
     * 消息类型
     */
    private String type = "pong";

    /**
     * 服务端响应时间戳
     */
    private Instant timestamp;

    public WsPongMessage(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
