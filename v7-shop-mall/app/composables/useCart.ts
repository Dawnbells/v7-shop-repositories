/**
 * 购物车 Composable
 *
 * 提供购物车状态管理，使用 localStorage 持久化
 * 支持商城模式（共享购物车）和单页模式（独立购物车）
 */

const CART_STORAGE_PREFIX = "v7-shop-cart";
const SPU_ID_COOKIE_KEY = "_spuId"; // 与服务端中间件 01-domain.ts 保持一致

/**
 * 购物车商品项
 */
export interface CartItem {
  id: string;
  productId: number;
  productName: string;
  specId: number | null;
  specAttributes: Array<{ name: string; value: string }>;
  price: number;
  originPrice?: number | null;
  quantity: number;
  image?: string;
  stockQuantity?: number;
}

/**
 * 直接下单商品项（不通过购物车）
 */
export interface DirectOrderItem {
  productId: number;
  productName: string;
  specId: number | null;
  specAttributes: Array<{ name: string; value: string }>;
  price: number;
  originPrice?: number | null;
  quantity: number;
  image?: string;
}

export function useCart() {
  const { siteConfig } = usePageContext();

  const cartMode = computed(
    () => siteConfig.value?.globalConfig?.cartMode ?? "single",
  );

  // 购物车商品列表
  const cartItems = useState<CartItem[]>("cartItems", () => []);

  // 直接下单商品（不通过购物车时使用）
  const directOrderItem = useState<DirectOrderItem | null>(
    "directOrderItem",
    () => null,
  );

  // 当前使用的 storage key（用于检测 key 变化）
  const currentStorageKey = useState<string>("currentCartStorageKey", () => "");

  // 购物车抽屉显示状态（全局共享）
  const cartDrawerVisible = useState<boolean>("cartDrawerVisible", () => false);

  // 获取当前域名
  function getDomain(): string {
    if (import.meta.server) return "default";
    return window.location.hostname;
  }

  // 获取当前商品 ID（从服务端中间件设置的 _spuId cookie 获取）
  function getProductId(): string {
    // 从 cookie 获取（由服务端中间件 01-domain.ts 设置）
    const cookie = useCookie<string>(SPU_ID_COOKIE_KEY);
    return cookie.value ?? "unknown";
  }

  // 获取购物车 storage key
  function getCartStorageKey(): string {
    const domain = getDomain();
    const mode = cartMode.value;

    if (mode === "single") {
      const productId = getProductId();
      return `${CART_STORAGE_PREFIX}-${domain}-${productId}`;
    }

    return `${CART_STORAGE_PREFIX}-${domain}`;
  }

  // 从 localStorage 加载购物车数据
  function loadFromStorage() {
    if (import.meta.server) return;

    const storageKey = getCartStorageKey();

    // 如果 key 变化了，重新加载
    if (currentStorageKey.value !== storageKey) {
      currentStorageKey.value = storageKey;
      cartItems.value = [];
    }

    try {
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed)) {
          cartItems.value = parsed;
        }
      }
    } catch (e) {
      console.warn("Failed to load cart from localStorage:", e);
    }
  }

  // 保存购物车数据到 localStorage
  function saveToStorage() {
    if (import.meta.server) return;

    const storageKey = getCartStorageKey();
    currentStorageKey.value = storageKey;

    try {
      localStorage.setItem(storageKey, JSON.stringify(cartItems.value));
    } catch (e) {
      console.warn("Failed to save cart to localStorage:", e);
    }
  }

  // 生成购物车项 ID
  function generateItemId(productId: number, specId: number | null): string {
    return `${productId}-${specId ?? "default"}`;
  }

  // 添加商品到购物车
  function addToCart(item: Omit<CartItem, "id">): void {
    loadFromStorage();

    const itemId = generateItemId(item.productId, item.specId);
    const existingIndex = cartItems.value.findIndex((i) => i.id === itemId);

    if (existingIndex >= 0) {
      // 已存在，增加数量
      const existing = cartItems.value[existingIndex];
      const newQuantity = existing.quantity + item.quantity;

      // 检查库存限制
      if (item.stockQuantity !== undefined && item.stockQuantity >= 0) {
        cartItems.value[existingIndex].quantity = Math.min(
          newQuantity,
          item.stockQuantity,
        );
      } else {
        cartItems.value[existingIndex].quantity = newQuantity;
      }

      // 更新价格等信息
      cartItems.value[existingIndex].price = item.price;
      cartItems.value[existingIndex].originPrice = item.originPrice;
      cartItems.value[existingIndex].image = item.image;
    } else {
      // 新增
      cartItems.value.push({
        ...item,
        id: itemId,
      });
    }

    saveToStorage();
  }

  // 从购物车移除商品
  function removeFromCart(itemId: string): void {
    loadFromStorage();
    cartItems.value = cartItems.value.filter((item) => item.id !== itemId);
    saveToStorage();
  }

  // 更新商品数量
  function updateQuantity(itemId: string, quantity: number): void {
    loadFromStorage();
    const index = cartItems.value.findIndex((item) => item.id === itemId);
    if (index >= 0) {
      if (quantity <= 0) {
        removeFromCart(itemId);
      } else {
        const item = cartItems.value[index];
        // 检查库存限制
        if (item.stockQuantity !== undefined && item.stockQuantity >= 0) {
          cartItems.value[index].quantity = Math.min(
            quantity,
            item.stockQuantity,
          );
        } else {
          cartItems.value[index].quantity = quantity;
        }
        saveToStorage();
      }
    }
  }

  // 清空购物车
  function clearCart(): void {
    cartItems.value = [];
    saveToStorage();
  }

  // 设置直接下单商品
  function setDirectOrderItem(item: DirectOrderItem | null): void {
    directOrderItem.value = item;
  }

  // 清除直接下单商品
  function clearDirectOrderItem(): void {
    directOrderItem.value = null;
  }

  // 购物车商品总数
  const cartCount = computed(() => {
    return cartItems.value.reduce((sum, item) => sum + item.quantity, 0);
  });

  // 购物车总价
  const cartTotal = computed(() => {
    return cartItems.value.reduce(
      (sum, item) => sum + item.price * item.quantity,
      0,
    );
  });

  // 购物车是否为空
  const isCartEmpty = computed(() => cartItems.value.length === 0);

  // 打开购物车抽屉
  function openCartDrawer(): void {
    loadFromStorage();
    cartDrawerVisible.value = true;
  }

  // 关闭购物车抽屉
  function closeCartDrawer(): void {
    cartDrawerVisible.value = false;
  }

  // 初始化：hydration 完成后再从 localStorage 读取，避免 SSR/客户端不一致导致 hydration mismatch
  if (import.meta.client) {
    onMounted(() => {
      loadFromStorage();
    });
  }

  return {
    cartItems,
    directOrderItem,
    cartDrawerVisible,
    addToCart,
    removeFromCart,
    updateQuantity,
    clearCart,
    setDirectOrderItem,
    clearDirectOrderItem,
    openCartDrawer,
    closeCartDrawer,
    cartCount,
    cartTotal,
    isCartEmpty,
    loadFromStorage,
  };
}
