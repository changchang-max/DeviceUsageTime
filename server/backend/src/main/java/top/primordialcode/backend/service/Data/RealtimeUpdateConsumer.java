package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.WebSocket.RealtimeBroadcaster;
import top.primordialcode.backend.utils.DataDateUtil;
import top.primordialcode.backend.utils.MaskUtil;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 上传事件的异步消费者
 *
 * 负责两件"不必让客户端等待"的事：
 * 1. MySQL 冷数据归档(逐应用 upsert + 窗口标题)；
 * 2. WebSocket 实时广播(合帧后推送)。
 *
 * 两者互不影响：归档失败只记日志(实时页仍然正常)，广播失败只记日志(数据已入库)。
 * 运行在 uploadExecutor 线程池里，与 HTTP 请求线程解耦。
 */
@Slf4j
@Component
public class RealtimeUpdateConsumer {

    @Autowired
    private DataArchiveServer dataArchiveServer;

    @Autowired
    private RedisDataUploadServer redisDataUploadServer;

    @Autowired
    private RealtimeBroadcaster realtimeBroadcaster;

    /**
     * 消费上传事件
     *
     * 上传的 Redis 写入在发布事件前已经完成，因此不需要事务阶段回调，
     * 用普通 @EventListener + @Async 即可。
     *
     * @param event 实时更新事件
     */
    @Async("uploadExecutor")
    @EventListener
    public void onRealtimeUpdate(RealtimeUpdateEvent event) {
        String userEmail = event.getUserEmail();
        LocalDate dataDate = DataDateUtil.toDataDate(event.getUploadTimestamp());

        // 1. 归档到 MySQL 冷数据(按用户+日期聚合)，失败只记日志
        try {
            dataArchiveServer.archive(userEmail, event.getUploadTimestamp(),
                    event.getApplications(), event.getStatistics());
        } catch (Exception e) {
            log.error("历史数据归档失败: email={}, date={}, error={}",
                    MaskUtil.maskEmail(userEmail), dataDate, e.getMessage(), e);
        }

        // 2. 测试数据不推送
        if (event.isTestData()) {
            log.debug("测试数据上传，跳过实时推送: userId={}, date={}",
                    MaskUtil.maskEmail(userEmail), dataDate);
            return;
        }

        // 3. 仅"今天"的数据才推送：补传历史只归档，不能覆盖查看者正在看的今日实时页
        if (!dataDate.equals(DataDateUtil.today())) {
            log.debug("非今日数据上传，跳过实时推送: userId={}, date={}",
                    MaskUtil.maskEmail(userEmail), dataDate);
            return;
        }

        // 4. 推送 Redis 中的完整快照(所有应用累计值 + 统计 + 最新时间戳)
        try {
            RedisDataUploadServer.Snapshot snapshot = redisDataUploadServer.readSnapshot(userEmail, dataDate);
            Instant latestTimestamp = (snapshot.getOtherData() != null
                    && snapshot.getOtherData().getTimestamp() != null)
                    ? snapshot.getOtherData().getTimestamp()
                    : event.getUploadTimestamp();

            String payload = realtimeBroadcaster.serialize(
                    userEmail,
                    latestTimestamp,
                    snapshot.getApplications(),
                    snapshot.getStatistics());
            realtimeBroadcaster.submit(userEmail, payload);
        } catch (Exception e) {
            // 推送失败不影响数据存储
            log.error("WebSocket实时推送失败: email={}, error={}",
                    MaskUtil.maskEmail(userEmail), e.getMessage(), e);
        }
    }

    /**
     * 事务回滚时记录日志，便于排查"数据未落库"的异常时序
     *
     * @param event 实时更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onRollback(RealtimeUpdateEvent event) {
        log.warn("上传事务回滚，取消实时推送: email={}", MaskUtil.maskEmail(event.getUserEmail()));
    }
}