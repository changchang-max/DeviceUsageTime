package top.primordialcode.backend.vo.data;

import lombok.Data;

import java.util.List;

/**
 * 有数据的日期列表返回VO
 */
@Data
public class DataDatesVO {

    /**
     * 查询的年月(YYYY-MM)
     */
    private String yearMonth;

    /**
     * 该月内有数据记录的日期列表(YYYY-MM-DD，按日期升序)
     */
    private List<String> dates;
}
