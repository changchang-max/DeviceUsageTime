package top.primordialcode.backend.service.WebSocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;


import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Component
public class DeviceWebSocketHandler
        extends TextWebSocketHandler {

    @Autowired
    ObjectMapper objectMapper;

    /**
     * 保存用户与WebSocket连接关系
     *
     * key:用户email
     * value:该用户所有浏览器窗口的Session
     *
     */
    // ConcurrentHashMap 是一个支持高并发访问的 HashMap。是一个线程安全的Map。todo 整理笔记
    private final Map<String, List<WebSocketSession>> users
            = new ConcurrentHashMap<>();

    /**
     * WebSocket连接建立
     *
     * 相当于 @OnOpen
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {
        /**
         * 从HandshakeInterceptor中获取用户email
         */
        // WebSocket 建立连接后，从握手阶段保存的数据中取出用户身份信息。里面的类型是Object，所以需要强转一下String
        String email = (String) session
                        .getAttributes()
                        .get("email");

        if(email == null){

            //没有用户身份，关闭连接
            session.close(
                    CloseStatus.NOT_ACCEPTABLE
            );

            return;
        }


        /**
         * 一个用户的信息可能被多个浏览器窗口打开
         * put()：无论有没有旧值，直接覆盖。
         * computeIfAbsent()：只有不存在这个 key 时，才创建并放入。
         *
         * CopyOnWriteArrayList是 JDK 提供的一个线程安全 List 实现。
         * 线程安全的原因：读操作直接读原数组，写操作时复制一份新数组，然后修改新数组，最后替换旧数组。
         */
        users.computeIfAbsent(
                        email,
                        key -> new CopyOnWriteArrayList<>()
                )
                .add(session);

        // 当前观看人数
        System.out.println(
                "WebSocket连接建立:"
                        + email
                        + " 当前连接数:"
                        + users.get(email).size()
        );

    }





    /**
     * 接收客户端消息
     *
     * 相当于 @OnMessage
     *
     * 一般你的项目中浏览器不会主动发送数据
     * 所以可以暂时不用
     */
    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message)
            throws Exception {


        String email = (String) session
                        .getAttributes()
                        .get("email");


        System.out.println(
                "收到客户端消息:"
                        + email
                        + " -> "
                        + message.getPayload()
        );

    }





    /**
     * WebSocket关闭
     *
     * 相当于 @OnClose
     */
    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status)
            throws Exception {


        String email = (String) session
                        .getAttributes()
                        .get("email");

        if(email == null){
            return;
        }

        List<WebSocketSession> sessions = users.get(email);

        if(sessions != null){

            //删除关闭的连接
            sessions.remove(session);

            /**
             * 如果该用户已经没有连接
             * 删除用户
             */
            if(sessions.isEmpty()){

                users.remove(email);

            }
        }

        System.out.println("WebSocket关闭:" + email);

    }

    /**
     * WebSocket异常
     *
     * 相当于 @OnError
     */
    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception)
            throws Exception {

        String email = (String) session
                        .getAttributes()
                        .get("email");

        System.out.println(
                "WebSocket异常:" + email + " " + exception.getMessage()
        );

        if(session.isOpen()){
            session.close(
                    CloseStatus.SERVER_ERROR
            );
        }
    }

    /**
     * 给指定用户推送消息
     *
     * 一个用户多个浏览器窗口都会收到
     */
    public void sendToUser(String email, DataUploadMainDTO data)
            throws Exception {
        //将DataUploadMainDTO对象序列化为json
        String message = objectMapper.writeValueAsString(data);

        // 获取用户对应的所以连接对象
        List<WebSocketSession> sessions = users.get(email);

        if(sessions == null){
            return;
        }

        for(WebSocketSession session : sessions){

            if(session.isOpen()){
                session.sendMessage(
                        new TextMessage(message)
                );

            }
        }
    }


    /**
     * 广播给所有用户
     *
     * 如果以后需要公告类消息可以使用
     */
    public void broadcast(DataUploadMainDTO data) throws Exception {
        //将DataUploadMainDTO对象序列化为json
        String message = objectMapper.writeValueAsString(data);

        for(List<WebSocketSession> sessions : users.values()){
            for(WebSocketSession session : sessions){
                if(session.isOpen()){
                    session.sendMessage(new TextMessage(message));
                }
            }
        }


    }
}