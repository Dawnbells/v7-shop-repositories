package cn.v7soft.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/notice")
public class NoticeController {
    @RequestMapping("/getList")
    public List<String> getList() {
        return List.of();
    }
}
