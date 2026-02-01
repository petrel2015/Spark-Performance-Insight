package com.spark.insight.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebViewController {

    /**
     * 将前端路由请求（如 /app/xxx, /job/xxx）全部转发给 index.html
     * 排除以 /api 开头的请求和带后缀的静态资源文件
     */
    @RequestMapping(value = {
        "/",
        "/app/**",
        "/compare/**",
        "/{path:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
