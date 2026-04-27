import type { ReactNode } from "react";
import Link from "@docusaurus/Link";
import Layout from "@theme/Layout";
import Heading from "@theme/Heading";

import styles from "./index.module.css";

const highlights = [
  { value: "Multi-site", label: "多站点独立运营" },
  { value: "SaaS", label: "租户隔离与权限体系" },
  { value: "H5", label: "商城页面快速交付" },
];

const capabilities = [
  {
    title: "运营后台",
    eyebrow: "Admin Console",
    description:
      "覆盖站点、商品、订单、域名、SSL、支付与营销配置，让日常运营动作清晰、稳定、可追踪。",
  },
  {
    title: "商城前台",
    eyebrow: "Storefront",
    description:
      "面向 H5、PC 与多终端访问场景，支持模板化页面、商品转化链路和品牌化视觉呈现。",
  },
  {
    title: "服务底座",
    eyebrow: "Service Layer",
    description:
      "以 Spring Boot 微服务承载鉴权、多租户、对象存储、DNS、消息与第三方订单同步能力。",
  },
];

const workflows = [
  "创建站点与品牌域名",
  "配置商品、库存与履约",
  "搭建 H5 商城页面",
  "同步订单与业务系统",
];

export default function Home(): ReactNode {
  return (
    <Layout title="V7 Shop" description="V7 Shop 多站点电商建站平台">
      <main className={styles.page}>
        <section className={styles.hero}>
          <div className={styles.heroShell}>
            <div className={styles.heroContent}>
              <span className={styles.kicker}>V7 Shop Commerce Platform</span>
              <Heading as="h1" className={styles.title}>
                专业级多站点电商 H5 建站平台
              </Heading>
              <p className={styles.subtitle}>
                从商城前台、运营后台到后端服务，把品牌站点、商品管理、订单同步和履约配置整合到一套稳定的商业基础设施中。
              </p>
              <div className={styles.actions}>
                <Link className={styles.primaryAction} to="/docs/intro">
                  开始使用
                </Link>
                <Link className={styles.secondaryAction} to="/docs/mall-guide/overview">
                  查看商城方案
                </Link>
              </div>
              <div className={styles.metrics}>
                {highlights.map((item) => (
                  <div className={styles.metric} key={item.label}>
                    <strong>{item.value}</strong>
                    <span>{item.label}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className={styles.heroVisual} aria-hidden="true">
              <div className={styles.orbit}>
                <span />
                <span />
                <span />
              </div>
              <div className={styles.dashboard}>
                <div className={styles.dashboardHeader}>
                  <span />
                  <span />
                  <span />
                </div>
                <div className={styles.dashboardBody}>
                  <div className={styles.revenueCard}>
                    <span>GMV Today</span>
                    <strong>¥ 1,286,420</strong>
                    <div className={styles.progress}>
                      <i />
                    </div>
                  </div>
                  <div className={styles.chart}>
                    <span />
                    <span />
                    <span />
                    <span />
                    <span />
                  </div>
                  <div className={styles.panelGrid}>
                    <div>
                      <b>96.8%</b>
                      <span>转化健康度</span>
                    </div>
                    <div>
                      <b>24</b>
                      <span>运行站点</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className={styles.capabilitySection}>
          <div className={styles.sectionHeader}>
            <span className={styles.kicker}>Core Capability</span>
            <Heading as="h2">把电商建站的关键链路收进一个专业工作台</Heading>
          </div>
          <div className={styles.capabilityGrid}>
            {capabilities.map((item, index) => (
              <article className={styles.capabilityCard} key={item.title}>
                <span className={styles.cardIndex}>0{index + 1}</span>
                <p>{item.eyebrow}</p>
                <Heading as="h3">{item.title}</Heading>
                <span>{item.description}</span>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.workflowSection}>
          <div className={styles.workflowVisual} aria-hidden="true">
            {workflows.map((item, index) => (
              <div className={styles.workflowNode} key={item}>
                <span>{index + 1}</span>
                <strong>{item}</strong>
              </div>
            ))}
          </div>
          <div className={styles.workflowCopy}>
            <span className={styles.kicker}>Delivery Flow</span>
            <Heading as="h2">从部署到上线，路径更短，交付更稳</Heading>
            <p>
              首页以产品级叙事组织信息：先建立平台定位，再展示能力边界和交付流程，适合对外介绍、客户演示和团队内部查阅。
            </p>
            <Link className={styles.textLink} to="/docs/third-party-order-sync/overview">
              了解第三方订单同步
            </Link>
          </div>
        </section>

        <section className={styles.ctaSection}>
          <div>
            <span className={styles.kicker}>Ready</span>
            <Heading as="h2">开始配置你的 V7 Shop 商城</Heading>
          </div>
          <Link className={styles.primaryAction} to="/docs/intro">
            进入操作手册
          </Link>
        </section>
      </main>
    </Layout>
  );
}
