package top.primordialcode.backend.vo.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 连接成功消息(服务端 → 客户端)
 *
 * 消息格式:
 * {
 *   "type": "connected",
 *   "message": "WebSocket连接成功",
 *   "userId": "xxx"          // 仅当用户以Token连接(即查看自己)时返回
 * }
 */
@Data
@NoArgsConstructor
public class WsConnectedMessage {

    /**
     * 消息类型
     */
    private String type = "connected";

    /**
     * 提示信息
     */
    private String message;

    /**
     * 用户标识(本系统中为用户邮箱)，仅Token认证(用户查看自己)时返回
     */
    private String userId;

    public WsConnectedMessage(String message, String userId) {
        this.message = message;
        this.userId = userId;
    }
}
