import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";
import type { LoadContext, Plugin } from "@docusaurus/types";
import fs from "node:fs";
import path from "node:path";

// 当前构建的语言（docusaurus 在构建每个 locale 时注入），用于站点标题等无法通过翻译 JSON 覆盖的字段
const currentLocale = process.env.DOCUSAURUS_CURRENT_LOCALE ?? "zh-Hans";
const isEn = currentLocale === "en";

/**
 * 短链接重定向：文档默认挂在 /docs 下，而 Shopify 应用声明要求隐私政策等页面使用不带 /docs 的固定地址
 * （如 https://docs.vantasite.com/en/privacy-policy）。构建结束后为每个 locale 写入静态跳转页。
 * key 为短路径，value 为目标文档路径（均不含 baseUrl / locale 前缀）。
 */
const shortLinks: Record<string, string> = {
  "privacy-policy": "docs/privacy-policy",
  "third-party-order-sync/shopify": "docs/third-party-order-sync/shopify",
};

function shortLinkRedirectPlugin(context: LoadContext): Plugin {
  return {
    name: "short-link-redirects",
    async postBuild({ outDir, baseUrl }) {
      const siteUrl = context.siteConfig.url;
      for (const [from, to] of Object.entries(shortLinks)) {
        const target = `${baseUrl}${to}`;
        const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Redirecting...</title>
<meta name="robots" content="noindex">
<link rel="canonical" href="${siteUrl}${target}">
<meta http-equiv="refresh" content="0; url=${target}">
<script>window.location.replace(${JSON.stringify(target)} + window.location.hash);</script>
</head>
<body><a href="${target}">${siteUrl}${target}</a></body>
</html>
`;
        const dir = path.join(outDir, from);
        // 已存在同名真实页面时不覆盖
        if (fs.existsSync(path.join(dir, "index.html"))) continue;
        fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(path.join(dir, "index.html"), html, "utf8");
      }
    },
  };
}

const config: Config = {
  title: isEn ? "VantaSite" : "VantaSite 梵塔独立站",
  tagline: isEn
    ? "Storefronts, landing pages and order sync for cross-border brands"
    : "外贸品牌自建站平台 — 独立站建站 · 投放落地页 · 订单同步",
  favicon: "img/favicon.ico",

  future: {
    v4: true,
  },

  url: "https://docs.vantasite.com",
  baseUrl: "/",

  organizationName: "Dawnbells",
  projectName: "vantasite-docs",

  onBrokenLinks: "throw",

  i18n: {
    defaultLocale: "zh-Hans",
    locales: ["zh-Hans", "en"],
    localeConfigs: {
      "zh-Hans": {
        label: "简体中文",
        htmlLang: "zh-Hans",
      },
      en: {
        label: "English",
        htmlLang: "en",
      },
    },
  },

  plugins: [shortLinkRedirectPlugin],

  presets: [
    [
      "classic",
      {
        docs: {
          sidebarPath: "./sidebars.ts",
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ["rss", "atom"],
            xslt: true,
          },
          onInlineTags: "warn",
          onInlineAuthors: "warn",
          onUntruncatedBlogPosts: "warn",
        },
        theme: {
          customCss: "./src/css/custom.css",
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: "img/docusaurus-social-card.jpg",
    colorMode: {
      defaultMode: "light",
      respectPrefersColorScheme: false,
      disableSwitch: false,
    },
    navbar: {
      title: "VantaSite",
      logo: {
        alt: "VantaSite Logo",
        src: "img/logo.svg",
      },
      items: [
        {
          type: "docSidebar",
          sidebarId: "tutorialSidebar",
          position: "left",
          label: "操作手册",
        },
        { to: "/blog", label: "更新日志", position: "left" },
        { type: "localeDropdown", position: "right" },
      ],
    },
    footer: {
      style: "dark",
      links: [
        {
          title: "产品能力",
          items: [
            {
              label: "外贸独立站",
              to: "/docs/mall-guide/overview",
            },
            {
              label: "商品与主题配置",
              to: "/docs/mall-guide/product-management",
            },
            {
              label: "第三方订单同步",
              to: "/docs/third-party-order-sync/overview",
            },
          ],
        },
        {
          title: "操作中心",
          items: [
            {
              label: "操作手册",
              to: "/docs/intro",
            },
            {
              label: "域名配置",
              to: "/docs/mall-guide/domain-setup",
            },
            {
              label: "实时同步",
              to: "/docs/third-party-order-sync/realtime-sync",
            },
          ],
        },
        {
          title: "版本与资源",
          items: [
            {
              label: "更新日志",
              to: "/blog",
            },
            {
              label: "历史同步",
              to: "/docs/third-party-order-sync/history-sync",
            },
            {
              label: "常见问题",
              to: "/docs/third-party-order-sync/faq",
            },
            {
              label: "隐私政策",
              to: "/docs/privacy-policy",
            },
          ],
        },
      ],
      copyright: `${isEn ? "VantaSite" : "VantaSite 梵塔独立站"} · Copyright © ${new Date().getFullYear()} VantaSite. All rights reserved.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ["java", "bash", "json"],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
