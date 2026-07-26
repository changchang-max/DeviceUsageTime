package top.primordialcode.backend.controller.api.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.service.Data.DataUploadServer;

@RestController
@RequestMapping(("/data"))
public class DataUploadController {
    @Autowired
    DataUploadServer dataUploadServer;

    @PostMapping("/upload")
    public Result upload(@RequestHeader String Authorization, @RequestBody DataUploadMainDTO data){
        return dataUploadServer.receive(Authorization,data);
    }
}
