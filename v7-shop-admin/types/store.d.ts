declare interface AclModuleType {
  admin: boolean
  permission: string[]
  role: string[]
}

declare interface BingModuleType {
  backgroundList: string[]
}

declare interface ErrorLogModuleType {
  errorLogs: any[]
}

declare interface RoutesModuleType {
  tab: {
    data: string | undefined
  }
  tabMenu: string | undefined
  activeMenu: {
    data: string | undefined
  }
  routes: any[]
  allRoutes: any[]
  breadcrumbRoutes: any[]
}

declare type DeviceType = 'mobile' | 'desktop'
declare type LanguageType = 'zh' | 'en'

declare interface SettingsModuleType {
  collapse: boolean
  device: DeviceType
  language: LanguageType
  lock: boolean
  logo: string
  mode: string
  persistenceTab: boolean
  theme: ThemeType
  title: string
  nickTitle: string
  scrollTop: []
}

declare interface TabsModuleType {
  caughtRoutes: []
  visitedRoutes: any[]
}

declare interface Currency {
  code: string
  name: string
  id: string
  symbol: string
}

declare interface UserModuleType {
  avatar: string
  displayName: string
  token: string | boolean
  username: string
  websiteManager: boolean
  currency: Currency | undefined
  website: WebsiteType | undefined
  imageBaseUrl?: string
}

declare interface WebsiteType {
  /*ID:ID */
  id: string

  /*ID序列号:ID的Base62格式 */
  compactId: string

  /*状态:状态,可用值:VALID,INVALID,DELETED */
  status: string

  /*归属人: */
  ownerName: string

  /*归属部门: */
  departmentName: string

  /*网站名称: */
  name: string

  /* */
  country: {
    /*ID:ID */
    id: string

    /*ID序列号:ID的Base62格式 */
    compactId: string

    /*状态:状态,可用值:VALID,INVALID,DELETED */
    status: string

    /*国家名称: */
    name: string

    /*国家代码: */
    code: string

    /* */
    currency: {
      /*ID:ID */
      id: string

      /*ID序列号:ID的Base62格式 */
      compactId: string

      /*状态:状态,可用值:VALID,INVALID,DELETED */
      status: string

      /*货币名称: */
      name: string

      /*货币符号: */
      symbol: string

      /*货币代码: */
      code: string

      /*美元兑换汇率: */
      exchangeRate: number

      /*有效小数位: */
      fractionDigits: number
    }

    /*语言信息: */
    languages: {
      /*ID:ID */
      id: string

      /*ID序列号:ID的Base62格式 */
      compactId: string

      /*状态:状态,可用值:VALID,INVALID,DELETED */
      status: string

      /*语言名称: */
      name: string

      /*语言中文名称: */
      cname: string

      /*语言代码: */
      code: string
    }[]

    /* */
    frontServer: {
      /*ID:ID */
      id: string

      /*ID序列号:ID的Base62格式 */
      compactId: string

      /*状态:状态,可用值:VALID,INVALID,DELETED */
      status: string

      /*服务器名称: */
      name: string

      /*cname域名:cname域名 */
      cnameRecord: string

      /*IP 地址: */
      ip: string

      /*解析次数: */
      resolutionCount: number

      /*当前有效解析数量: */
      activeResolutionCount: number
    }
  }

  /*语言信息: */
  languages: {
    /*ID:ID */
    id: string

    /*ID序列号:ID的Base62格式 */
    compactId: string

    /*状态:状态,可用值:VALID,INVALID,DELETED */
    status: string

    /*语言名称: */
    name: string

    /*语言中文名称: */
    cname: string

    /*语言代码: */
    code: string
  }[]

  /* */
  currency: {
    /*ID:ID */
    id: string

    /*ID序列号:ID的Base62格式 */
    compactId: string

    /*状态:状态,可用值:VALID,INVALID,DELETED */
    status: string

    /*货币名称: */
    name: string

    /*货币符号: */
    symbol: string

    /*货币代码: */
    code: string

    /*美元兑换汇率: */
    exchangeRate: number

    /*有效小数位: */
    fractionDigits: number
  }
}
