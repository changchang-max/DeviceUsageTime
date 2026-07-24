package top.primordialcode.backend.controller.index;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;

//@Controller是返回一个视图
@RestController
public class HomePageController {
    //主页（暂时用作鉴权测试）
    @GetMapping("/home")
    public Result homePage(){
        return Result.success();
    }
}
