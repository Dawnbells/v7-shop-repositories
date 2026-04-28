import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

const config: Config = {
  title: "VantaSite 梵塔独立站",
  tagline: "外贸品牌自建站平台 — 独立站建站 · 投放落地页 · 订单同步",
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
    locales: ["zh-Hans"],
  },

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
          ],
        },
      ],
      copyright: `VantaSite 梵塔独立站 · Copyright © ${new Date().getFullYear()} VantaSite. All rights reserved.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ["java", "bash", "json"],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
