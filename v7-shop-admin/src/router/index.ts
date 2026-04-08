/**
 * @description router全局配置，如有必要可分文件抽离，其中asyncRoutes只有在intelligence模式下才会用到，pro版只支持remixIcon图标，具体配置请查看vip群文档
 */
import type { App } from 'vue'
import type { RouteRecordName, RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { authentication, base, disableRouterWarning, isHashRouterMode } from '/@/config'
import { setupPermissions } from '/@/router/permissions'
import Layout from '/@vab/layouts/index.vue'

export const constantRoutes: VabRouteRecord[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('/@/views/login/Login.vue'),
    meta: {
      hidden: true,
    },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('/@/views/login/Register.vue'),
    meta: {
      hidden: true,
    },
  },
  {
    path: '/password',
    name: 'Password',
    component: () => import('/@/views/login/Password.vue'),
    meta: {
      hidden: true,
    },
  },
  {
    path: '/redirect',
    name: 'Redirect',
    component: () => import('/@/views/redirect/Redirect.vue'),
    meta: {
      hidden: true,
    },
  },
  {
    path: '/403',
    name: '403',
    component: () => import('/@/views/error/403.vue'),
    meta: {
      hidden: true,
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('/@/views/error/404.vue'),
    meta: {
      hidden: true,
    },
  },
]

export const asyncRoutes: VabRouteRecord[] = [
  // 首页
  {
    path: '/',
    name: 'Root',
    component: Layout,
    meta: {
      title: '首页',
      icon: 'home-2-line',
    },
    children: [
      {
        path: 'index',
        name: 'Index',
        component: () => import('/@/views/index/index.vue'),
        meta: {
          title: '首页',
          icon: 'home-2-line',
          noClosable: true,
          noKeepAlive: true,
        },
      },
      {
        path: 'cloakRiskControl',
        name: 'CloakRiskControl',
        component: () => import('/@/views/index/cloakRiskControl.vue'),
        meta: {
          title: '斗篷风控',
          icon: 'shield-line',
        },
      },
    ],
  },
  // AI 管理
  {
    path: '/ai',
    name: 'Ai',
    component: Layout,
    meta: {
      title: 'AI管理',
      icon: 'robot-line',
    },
    children: [
      {
        path: 'tokenUsage',
        name: 'AiTokenUsageRecord',
        component: () => import('/@/views/ai/AiTokenUsageRecord.vue'),
        meta: {
          title: 'Token使用记录',
          icon: 'coin-line',
        },
      },
    ],
  },
  // 404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    name: 'NotFound',
    meta: {
      title: '404',
      hidden: true,
    },
  },
]

const router = createRouter({
  history: isHashRouterMode ? createWebHashHistory(base) : createWebHistory(base),
  routes: constantRoutes as RouteRecordRaw[],
})

const fatteningRoutes = (routes: VabRouteRecord[]): VabRouteRecord[] => {
  return routes.flatMap((route) => {
    return route.children ? fatteningRoutes(route.children) : route
  })
}

const addRouter = (routes: VabRouteRecord[]) => {
  routes.forEach((route: VabRouteRecord) => {
    if (!router.hasRoute(route.name)) router.addRoute(route as RouteRecordRaw)
    if (route.children) addRouter(route.children)
  })
}

export const resetRouter = (routes: VabRouteRecord[] = constantRoutes) => {
  routes.map((route: VabRouteRecord) => {
    if (route.children) route.children = fatteningRoutes(route.children)
  })
  router.getRoutes().forEach((route) => {
    if (route.name) {
      const routeName: RouteRecordName = route.name
      router.hasRoute(routeName) && router.removeRoute(routeName)
    }
  })
  addRouter(routes)
}

export const setupRouter = (app: App<Element>) => {
  /*
   * @description: 控制台禁止出现[Vue Router warn]: No match found for location with path "/index"报黄
   * @tips: 未经全面测试，请谨慎使用！如遇问题请前往config/cli.config.ts配置disableRouterWarning:false
   * @author: @sundan
   */
  if (disableRouterWarning)
    router.addRoute({
      path: '/:pathMatch(.*)*',
      component: () => {},
    })

  if (authentication === 'intelligence') addRouter(asyncRoutes)
  setupPermissions(router)
  app.use(router)

  return router
}

export default router
