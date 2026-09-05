package top.primordialcode.backend.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 数据归属日期工具类
 *
 * 历史归档、实时数据分桶、日历日期标记等都按「自然日」进行，
 * 归属日期的换算必须使用统一的时区。
 * 本项目固定使用 Asia/Shanghai(与 application.yaml 中 datasource 的
 * serverTimezone=Asia/Shanghai 保持一致)，避免使用 UTC 时把东八区凌晨
 * (00:00-07:59) 上传的数据错误地归到前一天。
 */
public final class DataDateUtil {

    /** 数据归属日期的统一时区 */
    public static final ZoneId DATA_ZONE = ZoneId.of("Asia/Shanghai");

    private DataDateUtil() {
    }

    /**
     * 将上传时间戳换算为数据归属日期
     *
     * @param timestamp 上传时间戳(可空)
     * @return 归属日期; timestamp 为空时返回 null
     */
    public static LocalDate toDataDate(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.atZone(DATA_ZONE).toLocalDate();
    }

    /**
     * 当前数据归属日期(今天)
     */
    public static LocalDate today() {
        return LocalDate.now(DATA_ZONE);
    }
}
