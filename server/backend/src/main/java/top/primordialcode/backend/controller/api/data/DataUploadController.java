package top.primordialcode.backend.controller.api.data;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.service.Data.DataUploadServer;
import top.primordialcode.backend.vo.data.DataResponseVO;

@Slf4j
@RestController
@RequestMapping(("/data"))
public class DataUploadController {
    @Autowired
    DataUploadServer dataUploadServer;

    @PostMapping("/upload")
    public Result upload(@RequestHeader String Authorization, @Valid @RequestBody DataUploadMainDTO data){
        try {
            dataUploadServer.receive(Authorization, data);
        } catch (RuntimeException e) {
            // Redis 连接/序列化等异常统一在此捕获，记录日志后返回失败响应
            log.error("数据上传失败，Redis操作异常: {}", e.getMessage(), e);
            return Result.error(503, "数据上传失败，服务暂时不可用，请稍后重试", null);
        }

        DataResponseVO responseVO = new DataResponseVO();
        responseVO.setReceived(true);
        responseVO.setTimestamp(data.getTimestamp());
        
        return Result.success("数据上传成功", responseVO);
    }
}
