<script setup lang="ts">
/**
 * CustomHtml Block - 自定义 HTML 组件
 *
 * 在沙箱 <iframe srcdoc> 中渲染商家粘贴的任意 HTML。
 * 沙箱不含 allow-same-origin，用户代码运行在独立的不透明源中，
 * 无法访问商城的 Cookie / localStorage / DOM / 内部接口，
 * 也无法在后台 builder 中攻击 admin 会话。
 *
 * 安全红线（修改时务必遵守）：
 * 1. sandbox 绝不可加入 allow-same-origin（与 allow-scripts 同时存在会使沙箱失效）。
 * 2. 用户 HTML 只能进入 iframe 的 srcdoc，绝不可经 v-html/innerHTML 进入父文档。
 */

interface Props {
  /** 商家粘贴的 HTML 代码 */
  html?: string;
  /** 高度：'auto' 按内容自动撑高；具体值（如 '400px'）固定高度并内部滚动 */
  height?: string;
}

const props = withDefaults(defineProps<Props>(), {
  html: "",
  height: "auto",
});

// 编辑器上下文标记（由页面/画布 provide）
const isInEditor = inject<Ref<boolean>>("isInEditor", ref(false));

// 是否有内容
const hasHtml = computed(() => !!props.html && props.html.trim().length > 0);

// 是否固定高度
const isFixedHeight = computed(() => !!props.height && props.height !== "auto");

// iframe 元素引用与自动测量高度
const iframeEl = ref<HTMLIFrameElement | null>(null);
const autoHeight = ref<number | null>(null);

// 注入到 srcdoc 的受信撑高脚本（固定内容，非用户可控）
// 闭合标签写成 <\/script> 以免提前结束本 SFC 的 script 块
const RESIZE_SCRIPT =
  "<script>(function(){function p(){var h=Math.max(document.documentElement.scrollHeight||0,document.body?document.body.scrollHeight:0);parent.postMessage({__v7Resize:h},'*');}if(document.readyState==='complete'){p();}window.addEventListener('load',p);try{var r=new ResizeObserver(p);r.observe(document.documentElement);if(document.body){r.observe(document.body);}}catch(e){}setTimeout(p,50);setTimeout(p,300);setTimeout(p,1000);})();<\/script>";

// 组装 srcdoc：meta + 样式重置 + 用户 HTML + 撑高脚本
const srcdoc = computed(
  () =>
    '<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0}*{box-sizing:border-box}</style></head><body>' +
    (props.html || "") +
    RESIZE_SCRIPT +
    "</body></html>",
);

// iframe 高度
const frameHeight = computed(() => {
  if (isFixedHeight.value) return props.height as string;
  if (autoHeight.value != null) return autoHeight.value + "px";
  return "150px"; // SSR/未测量前的初始高度，客户端测量后自动修正
});

// 接收 iframe 上报的高度（仅信任本 iframe 的消息）
function onMessage(e: MessageEvent) {
  const el = iframeEl.value;
  if (!el || e.source !== el.contentWindow) return;
  const data = e.data as unknown;
  if (
    data &&
    typeof data === "object" &&
    typeof (data as Record<string, unknown>).__v7Resize === "number"
  ) {
    const h = (data as Record<string, number>).__v7Resize;
    if (h > 0 && h < 20000) autoHeight.value = Math.ceil(h);
  }
}

onMounted(() => {
  window.addEventListener("message", onMessage);
});

onBeforeUnmount(() => {
  window.removeEventListener("message", onMessage);
});
</script>

<template>
  <div class="custom-html-block">
    <template v-if="hasHtml">
      <!-- 安全红线：sandbox 不含 allow-same-origin；用户 HTML 仅经 :srcdoc 进入隔离 iframe -->
      <iframe
        ref="iframeEl"
        class="custom-html-frame"
        :srcdoc="srcdoc"
        sandbox="allow-scripts allow-popups allow-forms allow-popups-to-escape-sandbox allow-presentation"
        referrerpolicy="no-referrer"
        title="custom-html"
        :style="{ height: frameHeight }"
      />
      <!-- 编辑器内叠加透明层，保证区块可被选中/拖拽，并显示角标 -->
      <div v-if="isInEditor" class="custom-html-overlay">
        <span class="custom-html-badge">HTML</span>
      </div>
    </template>

    <!-- 空内容：编辑器显示占位，前台不渲染 -->
    <div v-else-if="isInEditor" class="custom-html-empty">
      请在右侧粘贴 HTML 代码
    </div>
  </div>
</template>

<style scoped>
.custom-html-block {
  position: relative;
  width: 100%;
}

.custom-html-frame {
  display: block;
  width: 100%;
  border: 0;
}

.custom-html-overlay {
  position: absolute;
  inset: 0;
  pointer-events: auto;
}

.custom-html-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 1px 6px;
  font-size: 11px;
  line-height: 1.4;
  color: #fff;
  background: rgba(59, 130, 246, 0.9);
  border-radius: 3px;
  pointer-events: none;
}

.custom-html-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
  padding: 16px;
  color: #94a3b8;
  font-size: 13px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.05);
}
</style>
