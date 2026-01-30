package cn.v7soft.accountservice.configurer;

import cn.hutool.core.collection.CollectionUtil;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.entities.meta.Meta;
import cn.v7soft.dao.enums.RouterPlatform;
import cn.v7soft.dao.enums.SystemRouterType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SystemRouterInitializer {

    public static List<SystemRouter> managerSystemRouters() {
        List<SystemRouter> systemRouterList = new ArrayList<>();

        SystemRouter homeRouter = SystemRouter.builder()
                .id(10000L)
                .path("/")
                .name("Root")
                .component("Layout")
                .meta(
                        Meta.builder()
                                .title("首页")
                                .icon("home-2-line")
                                .noClosable(false)
                                .build()
                )
                .platform(RouterPlatform.MANAGER)
                .type(SystemRouterType.MENU)
                .sortOrder(10000)
                .children(List.of(
                        SystemRouter.builder()
                                .id(10100L)
                                .path("index")
                                .name("Index")
                                .component("/@/views/index/index.vue")
                                .meta(
                                        Meta.builder()
                                                .title("首页")
                                                .icon("home-2-line")
                                                .noClosable(true)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(10100)
                                .build()
                ))
                .build();

        SystemRouter systemSettingRouter = SystemRouter.builder()
                .id(20000L)
                .path("/system")
                .name("System")
                .component("Layout")
                .meta(
                        Meta.builder()
                                .title("系统")
                                .icon("settings-2-line")
                                .noClosable(false)
                                .build()
                )
                .platform(RouterPlatform.MANAGER)
                .type(SystemRouterType.MENU)
                .children(List.of(
                        SystemRouter.builder()
                                .id(20100L)
                                .path("employee")
                                .name("Employee")
                                .component("/@/views/system/Employee.vue")
                                .meta(
                                        Meta.builder()
                                                .title("员工管理")
                                                .icon("user-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(20100)
                                .build(),

                        SystemRouter.builder()
                                .id(20200L)
                                .path("role")
                                .name("Role")
                                .component("/@/views/system/Role.vue")
                                .meta(
                                        Meta.builder()
                                                .title("角色管理")
                                                .icon("lock-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(20200)
                                .build(),

                        SystemRouter.builder()
                                .id(20300L)
                                .path("department")
                                .name("Department")
                                .component("/@/views/system/Department.vue")
                                .meta(
                                        Meta.builder()
                                                .title("部门管理")
                                                .icon("group-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(20300)
                                .build(),

                        SystemRouter.builder()
                                .id(20400L)
                                .path("menu")
                                .name("Menu")
                                .component("/@/views/system/Menu.vue")
                                .meta(
                                        Meta.builder()
                                                .title("菜单管理")
                                                .icon("menu-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(20400)
                                .build()
                ))
                .sortOrder(20000)
                .build();

        SystemRouter websiteRouter = SystemRouter.builder()
                .id(30000L)
                .path("/website")
                .name("Website")
                .component("Layout")
                .meta(
                        Meta.builder()
                                .title("商城")
                                .icon("home-2-line")
                                .noClosable(false)
                                .build()
                )
                .platform(RouterPlatform.MANAGER)
                .type(SystemRouterType.MENU)
                .sortOrder(30000)
                .children(List.of(
                        SystemRouter.builder()
                                .id(30100L)
                                .path("language")
                                .name("Language")
                                .component("/@/views/website/Language.vue")
                                .meta(
                                        Meta.builder()
                                                .title("语言")
                                                .icon("home-2-line")
                                                .noClosable(true)
                                                .build()
                                )
                                .platform(RouterPlatform.MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(30100)
                                .build()
                ))
                .build();

        systemRouterList.add(homeRouter);
        systemRouterList.add(websiteRouter);
        systemRouterList.add(systemSettingRouter);
        return systemRouterList;
    }

    public static List<SystemRouter> mallSystemRouters() {
        List<SystemRouter> systemRouterList = new ArrayList<>();

        SystemRouter homeRouter = SystemRouter.builder()
                .id(210000L)
                .path("/")
                .name("Root")
                .component("Layout")
                .meta(
                        Meta.builder()
                                .title("首页")
                                .icon("home-2-line")
                                .noClosable(false)
                                .build()
                )
                .platform(RouterPlatform.MALL_MANAGER)
                .type(SystemRouterType.MENU)
                .sortOrder(210000)
                .children(List.of(
                        SystemRouter.builder()
                                .id(210100L)
                                .path("index")
                                .name("Index")
                                .component("/@/views/index/index.vue")
                                .meta(
                                        Meta.builder()
                                                .title("首页")
                                                .icon("home-2-line")
                                                .noClosable(true)
                                                .build()
                                )
                                .platform(RouterPlatform.MALL_MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(210100)
                                .build()
                ))
                .build();

        SystemRouter systemSettingRouter = SystemRouter.builder()
                .id(220000L)
                .path("/system")
                .name("System")
                .component("Layout")
                .meta(
                        Meta.builder()
                                .title("系统")
                                .icon("settings-2-line")
                                .noClosable(false)
                                .build()
                )
                .platform(RouterPlatform.MALL_MANAGER)
                .type(SystemRouterType.MENU)
                .children(List.of(
                        SystemRouter.builder()
                                .id(220100L)
                                .path("employee")
                                .name("Employee")
                                .component("/@/views/system/Employee.vue")
                                .meta(
                                        Meta.builder()
                                                .title("员工管理")
                                                .icon("user-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MALL_MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(220100)
                                .build(),

                        SystemRouter.builder()
                                .id(220200L)
                                .path("role")
                                .name("Role")
                                .component("/@/views/system/Role.vue")
                                .meta(
                                        Meta.builder()
                                                .title("角色管理")
                                                .icon("lock-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MALL_MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(220200)
                                .build(),

                        SystemRouter.builder()
                                .id(220300L)
                                .path("department")
                                .name("Department")
                                .component("/@/views/system/Department.vue")
                                .meta(
                                        Meta.builder()
                                                .title("部门管理")
                                                .icon("group-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MALL_MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(220300)
                                .children(List.of(
                                        SystemRouter.builder()
                                                .id(230300L)
                                                .path("department")
                                                .name("Department")
                                                .component("/@/views/system/Department.vue")
                                                .meta(
                                                        Meta.builder()
                                                                .title("部门管理1")
                                                                .icon("group-line")
                                                                .noClosable(false)
                                                                .build()
                                                )
                                                .platform(RouterPlatform.MALL_MANAGER)
                                                .type(SystemRouterType.MENU)
                                                .sortOrder(230300)
                                                .build(),
                                        SystemRouter.builder()
                                                .id(240300L)
                                                .path("department")
                                                .name("Department")
                                                .component("/@/views/system/Department.vue")
                                                .meta(
                                                        Meta.builder()
                                                                .title("部门管理2")
                                                                .icon("group-line")
                                                                .noClosable(false)
                                                                .build()
                                                )
                                                .platform(RouterPlatform.MALL_MANAGER)
                                                .type(SystemRouterType.MENU)
                                                .sortOrder(240300)
                                                .build()
                                ))
                                .build(),

                        SystemRouter.builder()
                                .id(220400L)
                                .path("menu")
                                .name("Menu")
                                .component("/@/views/system/Menu.vue")
                                .meta(
                                        Meta.builder()
                                                .title("菜单管理")
                                                .icon("menu-2-line")
                                                .noClosable(false)
                                                .build()
                                )
                                .platform(RouterPlatform.MALL_MANAGER)
                                .type(SystemRouterType.MENU)
                                .sortOrder(220400)
                                .build()
                ))
                .sortOrder(220000)
                .build();


        systemRouterList.add(homeRouter);
        systemRouterList.add(systemSettingRouter);
        return systemRouterList;
    }


    public static List<SystemRouter> wrapCompanyId (List<SystemRouter> systemRouterList, Long id) {
        if (CollectionUtil.isEmpty(systemRouterList)) {
            return systemRouterList;
        }
        systemRouterList.forEach(r -> wrapCompanyId(r.getChildren(), id));
        return systemRouterList.stream().peek(r -> r.setCompanyId(id)).collect(Collectors.toList());
    }

}
