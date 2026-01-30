package cn.v7soft.admin.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/shopline")
public class ShoplineController {

    @GetMapping("/load")
    public void loadOrder() {
        log.debug("load order api");
    }
}
