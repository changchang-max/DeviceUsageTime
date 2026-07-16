package top.primordialcode.backend.controller.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class web_controller {
    @RequestMapping("/user")
    public String Test(){
        return "test";
    }
}
