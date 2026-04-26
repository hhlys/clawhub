package com.clawhub.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/login", "/register", "/my-instance", "/admin/users", "/admin/instances"})
    public String forward() {
        return "forward:/index.html";
    }
}
