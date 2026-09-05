package top.primordialcode.backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("数据归属日期工具测试")
class DataDateUtilTest {

    @Test
    @DisplayName("东八区凌晨(UTC前一日)的数据应归属到当天, 而不是前一天")
    void testLocalMidnightBelongsToSameDay() {
        // 2026-07-12T00:30+08:00 == 2026-07-11T16:30Z
        // 用UTC换算会错误得到07-11, 用Asia/Shanghai应得到07-12
        Instant instant = Instant.parse("2026-07-11T16:30:00Z");
        assertEquals(LocalDate.of(2026, 7, 12), DataDateUtil.toDataDate(instant));
    }

    @Test
    @DisplayName("东八区午夜临界点前后归属正确")
    void testZoneBoundary() {
        // 2026-07-12T23:59:59+08:00 -> 07-12
        Instant beforeMidnight = Instant.parse("2026-07-12T15:59:59Z");
        assertEquals(LocalDate.of(2026, 7, 12), DataDateUtil.toDataDate(beforeMidnight));

        // 2026-07-13T00:00+08:00 -> 07-13
        Instant afterMidnight = Instant.parse("2026-07-12T16:00:00Z");
        assertEquals(LocalDate.of(2026, 7, 13), DataDateUtil.toDataDate(afterMidnight));
    }

    @Test
    @DisplayName("白天时间戳归属不受时区影响")
    void testDaytime() {
        // 2026-07-12T10:30:45Z == 2026-07-12T18:30:45+08:00 -> 07-12
        Instant instant = Instant.parse("2026-07-12T10:30:45Z");
        assertEquals(LocalDate.of(2026, 7, 12), DataDateUtil.toDataDate(instant));
    }

    @Test
    @DisplayName("空时间戳返回null")
    void testNullTimestamp() {
        assertNull(DataDateUtil.toDataDate(null));
    }
}
