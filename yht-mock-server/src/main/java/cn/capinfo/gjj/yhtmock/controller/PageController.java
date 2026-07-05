package cn.capinfo.gjj.yhtmock.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/yht-mock")
    public String redirectToIndex() {
        return "redirect:/yht-mock/index.html";
    }

    @GetMapping("/yht-mock/")
    public String redirectToIndexWithSlash() {
        return "redirect:/yht-mock/index.html";
    }
}
