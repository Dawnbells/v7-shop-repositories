//package cn.v7soft.accountservice.controller;
//
//import cn.dev33.satoken.config.SaSsoConfig;
//import cn.dev33.satoken.context.SaHolder;
//import cn.dev33.satoken.sso.SaSsoProcessor;
//import cn.dev33.satoken.stp.StpUtil;
//import cn.dev33.satoken.util.SaResult;
//import cn.v7soft.core.annotation.IgnoreResponsePackage;
//import com.dtflys.forest.Forest;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.validation.annotation.Validated;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.servlet.ModelAndView;
//
///**
// * Sa-Token-SSO Server端 Controller
// */
//@RestController
//@Validated
//@Slf4j
//@IgnoreResponsePackage
//@RequestMapping("/sso")
//public class SsoServerController {
//    /**
//     * 处理 SSO-Server 端所有请求
//     */
//    @RequestMapping({"/auth", "/doLogin", "/checkTicket", "/signOut"})
//    public Object ssoServerRequest() {
//        return SaSsoProcessor.instance.serverDister();
//    }
//
//    // 配置SSO相关参数
//    @Autowired
//    public void configSso(SaSsoConfig sso) {
//        // 配置：未登录时返回的View
//        sso.setNotLoginView(() -> new ModelAndView("login.html"));
//
//        // 配置：登录处理函数
//        sso.setDoLoginHandle((name, pwd) -> {
//            // 此处仅做模拟登录，真实环境应该查询数据进行登录
//            String username = SaHolder.getRequest().getParam("username");
//            String password = SaHolder.getRequest().getParam("password");
//            if ("admin".equals(username) && "123456".equals(password)) {
//                StpUtil.login(10001);
//                return SaResult.ok("登录成功！").setData(StpUtil.getTokenValue());
//            }
//            return SaResult.error("登录失败！");
//        });
//
//        // 配置 Http 请求处理器 （在模式三的单点注销功能下用到，如不需要可以注释掉）
//        sso.setSendHttp(url -> {
//            try {
//                // 发起 http 请求
//                System.out.println("------ 发起请求：" + url);
//                return Forest.get(url).executeAsString();
//            } catch (Exception e) {
//                e.printStackTrace();
//                return null;
//            }
//        });
//    }
//}