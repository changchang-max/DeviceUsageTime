package top.primordialcode.backend.controller.api.data;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.service.Data.DataUploadServer;
import top.primordialcode.backend.vo.data.DataResponseVO;

@RestController
@RequestMapping(("/data"))
public class DataUploadController {
    @Autowired
    DataUploadServer dataUploadServer;

    @PostMapping("/upload")
    public Result upload(@RequestHeader String Authorization, @Valid @RequestBody DataUploadMainDTO data){
        dataUploadServer.receive(Authorization, data);
        
        DataResponseVO responseVO = new DataResponseVO();
        responseVO.setReceived(true);
        responseVO.setTimestamp(data.getTimestamp());
        
        return Result.success("数据上传成功", responseVO);
    }
}
