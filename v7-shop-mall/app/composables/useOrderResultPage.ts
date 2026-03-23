/**
 * 订单结果页 Composable
 *
 * 提供订单结果相关的数据和方法：
 * - 订单数据（从 usePageContext 获取，由中间件注入或编辑器 mock）
 *
 * SSR 时所有数据在服务端由中间件获取，客户端通过 hydration 恢复
 * 编辑器模式下通过 setPreviewData 注入 mock 数据
 */

export function useOrderResultPage() {
  const { orderResult } = usePageContext();

  return {
    orderResult,
  };
}
