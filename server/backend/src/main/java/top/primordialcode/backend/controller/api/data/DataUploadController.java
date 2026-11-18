package top.primordialcode.backend.controller.api.data;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.service.Data.DataUploadServer;
import top.primordialcode.backend.vo.data.DataResponseVO;

/**
 * 数据上传控制器
 *
 * 请求线程只等待"校验 + Redis 写入"，MySQL 归档与 WebSocket 推送已异步化，
 * 因此正常路径的耗时稳定在个位数毫秒级。
 * 错误由全局异常处理器统一映射(参数错误 400、依赖不可用 503)。
 */
@Slf4j
@RestController
@RequestMapping("/data")
public class DataUploadController {

    @Autowired
    private DataUploadServer dataUploadServer;

    /**
     * 接收客户端上传的使用数据
     *
     * @param data 上传数据
     * @return 受理结果(含服务端收到的时间戳回显)
     */
    @PostMapping("/upload")
    public Result upload(@Valid @RequestBody DataUploadMainDTO data) {
        dataUploadServer.receive(data);

        DataResponseVO responseVO = new DataResponseVO();
        responseVO.setReceived(true);
        responseVO.setTimestamp(data.getTimestamp());
        return Result.success("数据上传成功", responseVO);
    }
}