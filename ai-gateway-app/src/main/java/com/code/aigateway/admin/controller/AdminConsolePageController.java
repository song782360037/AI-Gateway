package com.code.aigateway.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端管理台页面入口
 */
@Controller
public class AdminConsolePageController {

    /**
     * 跳转到独立 Vue 管理台页面。
     *
     * @return 前端静态页面跳转地址
     */
    @GetMapping({"/admin-console", "/admin-console/"})
    public String index() {
        return "redirect:/frontend-vue/index.html";
    }
}
