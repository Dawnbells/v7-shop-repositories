package cn.v7soft.admin.service.dns;

import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;

import java.time.LocalDateTime;

public interface IDnsService {
    /**
     * 平台标识
     */
    CloudPlatform getPlatform();

    /**
     * 更新域名解析到指定 IP
     *
     * @param domainName 主域名
     * @param subName    子名
     * @param ip         新的 IP 地址
     * @return
     */
    boolean updateRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName, String ip);
    /**
     * 删除域名解析记录
     *
     * @return
     */
    boolean deleteRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName);

    /**
     * 查询域名当前解析的 IP
     */
    String queryRecord(CloudPlatformAccount cloudPlatformAccount, String domainName, String subName);

    /**
     * 查询域名过期时间
     *
     * @param cloudPlatformAccount 云平台账户
     * @param domainName           域名名称
     * @return 域名过期时间，获取失败返回 null
     */
    LocalDateTime queryDomainExpiryDate(CloudPlatformAccount cloudPlatformAccount, String domainName);
}
