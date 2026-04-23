import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

const config: Config = {
  title: "V7 Shop 操作手册",
  tagline: "多站点电商建站平台 — 管理后台 · 商城前台 · 后端服务",
  favicon: "img/favicon.ico",

  future: {
    v4: true,
  },

  url: "https://docs.v7soft.cn",
  baseUrl: "/",

  organizationName: "Dawnbells",
  projectName: "v7-shop-docs",

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
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: "V7 Shop",
      logo: {
        alt: "V7 Shop Logo",
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
          title: "文档",
          items: [
            {
              label: "操作手册",
              to: "/docs/intro",
            },
            {
              label: "Mall 商城模板",
              to: "/docs/category/Mall 商城模板使用教程",
            },
            {
              label: "第三方订单同步",
              to: "/docs/category/第三方商城订单同步",
            },
          ],
        },
        {
          title: "更多",
          items: [
            {
              label: "更新日志",
              to: "/blog",
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} V7Soft. All rights reserved.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ["java", "bash", "json"],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
