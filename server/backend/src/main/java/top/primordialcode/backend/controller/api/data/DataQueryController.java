package top.primordialcode.backend.controller.api.data;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.service.Data.DataQueryServer;
import top.primordialcode.backend.vo.data.DataDatesVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

import java.util.List;

/**
 * 数据查询控制器
 *
 * 只负责接收参数、调用 Service、返回统一响应；异常全部交给
 * {@link top.primordialcode.backend.exception.GlobalExceptionHandler} 统一映射，
 * 不再在每个方法里手写 try/catch。
 */
@Slf4j
@RestController
@RequestMapping("/data")
public class DataQueryController {

    @Autowired
    private DataQueryServer dataQueryServer;

    /**
     * 获取用户当前实时数据
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     *
     * @param authorization Bearer token，可选
     * @param key           用户秘钥，可选
     * @param request       HTTP 请求(用于取客户端IP做限流)
     * @return 实时数据
     */
    @GetMapping("/realtime")
    public Result getRealtime(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key,
            HttpServletRequest request) {
        RealtimeDataVO vo = dataQueryServer.getRealtimeData(authorization, key, clientIp(request));
        return Result.success("获取实时数据成功", vo);
    }

    /**
     * 获取指定日期的历史数据
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     *
     * @param date          日期(YYYY-MM-DD)
     * @param includeTitles 是否同时返回窗口标题，默认 false(由懒加载接口单独取)
     * @param authorization Bearer token，可选
     * @param key           用户秘钥，可选
     * @param request       HTTP 请求(用于取客户端IP做限流)
     * @return 历史数据
     */
    @GetMapping("/history")
    public Result getHistory(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "includeTitles", required = false, defaultValue = "false") boolean includeTitles,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key,
            HttpServletRequest request) {
        HistoryDataVO vo = dataQueryServer.getHistoryData(
                authorization, key, clientIp(request), date, includeTitles);
        return Result.success("获取历史数据成功", vo);
    }

    /**
     * 懒加载某应用在某天的窗口标题(历史页展开时调用)
     *
     * @param date          日期(YYYY-MM-DD)
     * @param appName       应用名称
     * @param authorization Bearer token，可选
     * @param key           用户秘钥，可选
     * @param request       HTTP 请求(用于取客户端IP做限流)
     * @return 窗口标题列表
     */
    @GetMapping("/titles")
    public Result getTitles(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key,
            HttpServletRequest request) {
        List<String> titles = dataQueryServer.getWindowTitles(
                authorization, key, clientIp(request), date, appName);
        return Result.success("获取窗口标题成功", titles);
    }

    /**
     * 获取某个月份内有数据记录的日期列表(用于前端日历标记)
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     *
     * @param yearMonth     年月(YYYY-MM)
     * @param authorization Bearer token，可选
     * @param key           用户秘钥，可选
     * @param request       HTTP 请求(用于取客户端IP做限流)
     * @return 有数据日期列表
     */
    @GetMapping("/dates")
    public Result getDataDates(
            @RequestParam(value = "yearMonth", required = false) String yearMonth,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key,
            HttpServletRequest request) {
        DataDatesVO vo = dataQueryServer.getDataDates(authorization, key, clientIp(request), yearMonth);
        return Result.success("获取日期列表成功", vo);
    }

    /**
     * 提取客户端真实IP(优先取反向代理头)
     *
     * @param request HTTP 请求
     * @return 客户端IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}