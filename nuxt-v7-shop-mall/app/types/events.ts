/**
 * 事件类型定义
 */

// 事件触发方式
export type EventTrigger =
  | "click"
  | "hover"
  | "focus"
  | "blur"
  | "change"
  | "submit"
  | "load";

// 事件动作类型
export type EventActionType =
  | "navigate"      // 页面跳转
  | "openUrl"       // 打开链接
  | "showModal"     // 显示弹窗
  | "hideModal"     // 隐藏弹窗
  | "scrollTo"      // 滚动到
  | "addToCart"     // 加入购物车
  | "checkout"      // 结算
  | "custom";       // 自定义

// 事件绑定
export interface EventBinding {
  trigger: EventTrigger;
  action: EventActionType;
  payload?: Record<string, any>;
}
