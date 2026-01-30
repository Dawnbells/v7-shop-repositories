package cn.v7soft.entrance.controller;

import cn.hutool.http.HttpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/")
    public String test() {
       return "hello v7 shop";
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String s = HttpUtil.get("https://ipinfo.io/widget/demo/61.223.141.202?dataset=proxy-vpn-detection");
        System.out.println(s);
        System.out.println(System.currentTimeMillis() - start);
    }
}
