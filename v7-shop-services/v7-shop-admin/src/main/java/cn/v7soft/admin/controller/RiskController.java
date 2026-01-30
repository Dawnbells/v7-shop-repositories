package cn.v7soft.admin.controller;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/risk")
public class RiskController {
    @RequestMapping("webhook")
    public void webhook(@RequestBody Map<String, Object> requestBody) {
        log.debug(JSONUtil.toJsonPrettyStr(requestBody));
        log.debug("webhook >>> use time: >> " +( ((long)requestBody.get("callback_sent")) - ((long)requestBody.get("js_served_at"))));
    }
}
