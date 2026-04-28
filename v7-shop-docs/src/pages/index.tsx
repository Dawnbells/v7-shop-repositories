import type { ReactNode } from "react";
import Link from "@docusaurus/Link";
import Layout from "@theme/Layout";
import Heading from "@theme/Heading";

import styles from "./index.module.css";

const highlights = [
  { value: "DTC", label: "外贸品牌独立站" },
  { value: "Multi-site", label: "多市场站点运营" },
  { value: "H5", label: "投放落地页快速交付" },
];

const capabilities = [
  {
    title: "运营后台",
    eyebrow: "Admin Console",
    description:
      "覆盖站点、商品、订单、域名、SSL、支付与投放配置，让外贸团队的日常运营动作清晰、稳定、可追踪。",
  },
  {
    title: "独立站前台",
    eyebrow: "Storefront",
    description:
      "面向 H5、PC 与多终端访问场景，支持品牌独立站、商品转化链路和投放落地页的视觉呈现。",
  },
  {
    title: "服务底座",
    eyebrow: "Service Layer",
    description:
      "以 Spring Boot 微服务承载鉴权、多租户、对象存储、DNS、消息与第三方平台订单同步能力。",
  },
];

const workflows = [
  "创建站点与品牌域名",
  "配置商品、库存与履约",
  "搭建 H5 独立站页面",
  "同步订单与业务系统",
];

export default function Home(): ReactNode {
  return (
    <Layout title="VantaSite" description="VantaSite 梵塔独立站外贸品牌自建站平台">
      <main className={styles.page}>
        <section className={styles.hero}>
          <div className={styles.heroShell}>
            <div className={styles.heroContent}>
              <span className={styles.kicker}>VantaSite Global Commerce Builder</span>
              <Heading as="h1" className={styles.title}>
                外贸品牌独立站与 H5 增长平台
              </Heading>
              <p className={styles.subtitle}>
                从独立站前台、运营后台到后端服务，把品牌站点、投放落地页、商品管理、订单同步和履约配置整合到一套稳定的出海增长基础设施中。
              </p>
              <div className={styles.actions}>
                <Link className={styles.primaryAction} to="/docs/intro">
                  开始使用
                </Link>
                <Link className={styles.secondaryAction} to="/docs/mall-guide/overview">
                  查看建站方案
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
            <Heading as="h2">把外贸自建站的关键链路收进一个专业工作台</Heading>
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
            <Heading as="h2">开始配置你的 VantaSite 独立站</Heading>
          </div>
          <Link className={styles.primaryAction} to="/docs/intro">
            进入操作手册
          </Link>
        </section>
      </main>
    </Layout>
  );
}
