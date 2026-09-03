package top.primordialcode.backend.controller.api.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.exception.DataNotFoundException;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.service.Data.DataQueryServer;
import top.primordialcode.backend.vo.data.DataDatesVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

@Slf4j
@RestController
@RequestMapping("/data")
public class DataQueryController {

    @Autowired
    DataQueryServer dataQueryServer;

    /**
     * 获取用户当前实时数据
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     * @param authorization Bearer token，可选
     * @param key 用户秘钥，可选
     * @return 实时数据
     */
    @GetMapping("/realtime")
    public Result getRealtime(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key) {
        try {
            RealtimeDataVO vo = dataQueryServer.getRealtimeData(authorization, key);
            return Result.success("获取实时数据成功", vo);
        } catch (SecurityException e) {
            log.warn("获取实时数据认证失败: {}", e.getMessage());
            return Result.error(401, "Token或秘钥无效", null);
        } catch (UserNotFoundException e) {
            log.warn("获取实时数据失败: {}", e.getMessage());
            return Result.error(404, "用户不存在", null);
        }
    }

    /**
     * 获取指定日期的历史数据
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     * @param date 日期(YYYY-MM-DD)
     * @param authorization Bearer token，可选
     * @param key 用户秘钥，可选
     * @return 历史数据
     */
    @GetMapping("/history")
    public Result getHistory(
            @RequestParam(value = "date", required = false) String date,//时间信息是必须的
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key) {
        try {
            HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, key, date);
            return Result.success("获取历史数据成功", vo);
        } catch (SecurityException e) {
            log.warn("获取历史数据认证失败: {}", e.getMessage());
            return Result.error(401, "Token或秘钥无效", null);
        } catch (IllegalArgumentException e) {
            log.warn("获取历史数据参数错误: {}", e.getMessage());
            return Result.error(400, "日期格式错误", null);
        } catch (UserNotFoundException e) {
            log.warn("获取历史数据失败: {}", e.getMessage());
            return Result.error(404, "用户不存在", null);
        } catch (DataNotFoundException e) {
            log.warn("获取历史数据失败: {}", e.getMessage());
            return Result.error(404, "该日期无数据", null);
        }
    }

    /**
     * 获取某个月份内有数据记录的日期列表(用于前端日历标记)
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     * @param yearMonth 年月(YYYY-MM)
     * @param authorization Bearer token，可选
     * @param key 用户秘钥，可选
     * @return 有数据日期列表
     */
    @GetMapping("/dates")
    public Result getDataDates(
            @RequestParam(value = "yearMonth", required = false) String yearMonth,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key) {
        try {
            DataDatesVO vo = dataQueryServer.getDataDates(authorization, key, yearMonth);
            return Result.success("获取日期列表成功", vo);
        } catch (SecurityException e) {
            log.warn("获取日期列表认证失败: {}", e.getMessage());
            return Result.error(401, "Token或秘钥无效", null);
        } catch (IllegalArgumentException e) {
            log.warn("获取日期列表参数错误: {}", e.getMessage());
            return Result.error(400, "年月格式错误", null);
        } catch (UserNotFoundException e) {
            log.warn("获取日期列表失败: {}", e.getMessage());
            return Result.error(404, "用户不存在", null);
        }
    }
}
