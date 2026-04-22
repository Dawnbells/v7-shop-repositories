import type { ReactNode } from "react";
import clsx from "clsx";
import Heading from "@theme/Heading";
import styles from "./styles.module.css";

type FeatureItem = {
  title: string;
  icon: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: "管理后台",
    icon: "🖥️",
    description: (
      <>
        基于 Vue 3 + Element Plus
        的运营管理面板，覆盖站点管理、商品目录、订单处理、域名/SSL、风控策略等全流程操作。
      </>
    ),
  },
  {
    title: "商城前台",
    icon: "🛒",
    description: (
      <>
        基于 Nuxt 4 的多站点商城渲染引擎，支持可视化模板编辑、区块化页面构建，轻松打造高转化率店铺。
      </>
    ),
  },
  {
    title: "后端服务",
    icon: "⚙️",
    description: (
      <>
        Spring Boot 3 微服务架构，内置多租户隔离、Sa-Token 鉴权、对象存储、DNS
        管理、AI 能力等基础设施。
      </>
    ),
  },
];

function Feature({ title, icon, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4")}>
      <div className="text--center">
        <span className={styles.featureIcon}>{icon}</span>
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
