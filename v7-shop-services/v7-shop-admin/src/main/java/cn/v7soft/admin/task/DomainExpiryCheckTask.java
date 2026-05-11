package cn.v7soft.admin.task;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IFrontServerService;
import cn.v7soft.admin.service.INoticeService;
import cn.v7soft.admin.service.ITopLevelDomainService;
import cn.v7soft.admin.utils.NginxConfigWriter;
import cn.v7soft.common.utils.SslCertificateUtil;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.FrontServer;
import cn.v7soft.dao.entities.primary.SSLCertificate;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 域名证书过期检查定时任务
 * <p>
 * 每日早上9:00执行，检查所有域名的SSL证书和域名有效期：
 * <ol>
 *   <li>同步实际证书文件有效期到数据库</li>
 *   <li>证书3天内过期 → 通知域名所有人即时续期</li>
 *   <li>证书已过期 → 通知域名所有人已过期天数</li>
 *   <li>证书过期≥5天或未设置 → 通知3天后将删除</li>
 *   <li>连续通知3次（3天）后未处理 → 自动删除域名</li>
 *   <li>域名本身过期也按同样流程处理</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainExpiryCheckTask {

    private static final String NOTICE_TYPE_DOMAIN = "DOMAIN";
    private static final String CERT_DIR = "/www/certs/";
    private static final String NGINX_DIR = "/www/nginx/";
    private static final int EXPIRY_WARNING_DAYS = 3;
    private static final int DELETION_TRIGGER_EXPIRED_DAYS = 5;
    private static final int MAX_DELETION_NOTICES = 3;

    private final Environment environment;
    private final TransactionTemplate transactionTemplate;
    private final TopLevelDomainRepository topLevelDomainRepository;
    private final ITopLevelDomainService topLevelDomainService;
    private final IFrontServerService frontServerService;
    private final INoticeService noticeService;
    private final ICompanyService companyService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void checkDomainExpiry() {
        if (isDevProfile()) {
            log.debug("[DomainExpiryCheck] 开发环境，跳过域名巡检");
            return;
        }

        log.info("[DomainExpiryCheck] 开始域名证书和有效期巡检");

        List<TopLevelDomain> domains;
        try {
            TenantContext.silent();
            domains = topLevelDomainRepository.findAllValidDomains();
            log.info("[DomainExpiryCheck] 共发现 {} 个域名需要检查", domains.size());
        } catch (Exception e) {
            log.error("[DomainExpiryCheck] 查询域名列表异常", e);
            return;
        } finally {
            TenantContext.clear();
        }

        int synced = 0, warned = 0, expired = 0, deletionNotified = 0, deleted = 0;

        for (TopLevelDomain domain : domains) {
            try {
                setTenantForDomain(domain);
                DomainCheckResult result = transactionTemplate.execute(status -> checkSingleDomain(domain));
                if (result != null) {
                    switch (result) {
                        case SYNCED -> synced++;
                        case EXPIRY_WARNING -> warned++;
                        case EXPIRED_NOTICE -> expired++;
                        case DELETION_NOTICE -> deletionNotified++;
                        case DELETED -> deleted++;
                        default -> {}
                    }
                }
            } catch (Exception e) {
                log.error("[DomainExpiryCheck] 检查域名 {} (id={}) 失败", domain.getName(), domain.getId(), e);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("[DomainExpiryCheck] 巡检完成: 同步={}, 即将过期警告={}, 已过期通知={}, 删除预告={}, 已删除={}",
                synced, warned, expired, deletionNotified, deleted);

        cleanupOrphanedResources(domains);
    }

    private DomainCheckResult checkSingleDomain(TopLevelDomain domain) {
        LocalDateTime actualCertExpiry = readActualCertExpiry(domain);
        boolean certSynced = syncCertExpiryToDb(domain, actualCertExpiry);

        // 判断证书状态
        boolean certMissing = (actualCertExpiry == null);
        boolean certExpired = !certMissing && actualCertExpiry.isBefore(LocalDateTime.now());
        long certExpiredDays = certExpired ? ChronoUnit.DAYS.between(actualCertExpiry, LocalDateTime.now()) : 0;
        boolean certExpiringWithin3Days = !certMissing && !certExpired
                && actualCertExpiry.isBefore(LocalDateTime.now().plusDays(EXPIRY_WARNING_DAYS));

        // 判断域名注册过期状态
        LocalDateTime domainExpiry = domain.getExpiryDate();
        boolean domainExpired = domainExpiry != null && domainExpiry.isBefore(LocalDateTime.now());
        long domainExpiredDays = domainExpired ? ChronoUnit.DAYS.between(domainExpiry, LocalDateTime.now()) : 0;
        boolean domainExpiringWithin3Days = domainExpiry != null && !domainExpired
                && domainExpiry.isBefore(LocalDateTime.now().plusDays(EXPIRY_WARNING_DAYS));

        Long ownerId = getOwnerId(domain);
        boolean inRequestProgress = domain.getCertificateRequestStatus() == CertificateRequestStatus.QUEUE
                || domain.getCertificateRequestStatus() == CertificateRequestStatus.REQUESTING;

        // 1. 证书过期≥5天 或 证书未设置 → 进入删除流程（申请进行中除外，避免误删新建域名）
        if (!inRequestProgress && (certMissing || certExpiredDays >= DELETION_TRIGGER_EXPIRED_DAYS)) {
            return handleDeletionFlow(domain, ownerId, certMissing
                    ? "证书未设置"
                    : "证书已过期" + certExpiredDays + "天");
        }

        // 2. 域名注册过期≥5天 → 同样进入删除流程
        if (domainExpiredDays >= DELETION_TRIGGER_EXPIRED_DAYS) {
            return handleDeletionFlow(domain, ownerId, "域名已过期" + domainExpiredDays + "天");
        }

        // 如果之前进入过删除流程但现在恢复了，重置计数
        if (domain.getDeletionNoticeCount() != null && domain.getDeletionNoticeCount() > 0) {
            domain.setDeletionNoticeCount(0);
            topLevelDomainRepository.save(domain);
        }

        // 3. 证书已过期（<5天）→ 发送过期通知
        if (certExpired) {
            sendNotice(ownerId, domain,
                    "域名证书已过期",
                    "您的域名【" + domain.getName() + "】的SSL证书已过期" + certExpiredDays + "天，请尽快续期。");
            return DomainCheckResult.EXPIRED_NOTICE;
        }

        // 4. 域名注册已过期（<5天）→ 发送过期通知
        if (domainExpired) {
            sendNotice(ownerId, domain,
                    "域名已过期",
                    "您的域名【" + domain.getName() + "】已过期" + domainExpiredDays + "天，请尽快续期。");
            return DomainCheckResult.EXPIRED_NOTICE;
        }

        // 5. 证书3天内过期 → 发送即将过期警告
        if (certExpiringWithin3Days) {
            long remainDays = ChronoUnit.DAYS.between(LocalDateTime.now(), actualCertExpiry);
            sendNotice(ownerId, domain,
                    "域名证书即将过期",
                    "您的域名【" + domain.getName() + "】的SSL证书将在" + remainDays + "天后过期，请及时续期。");
            return DomainCheckResult.EXPIRY_WARNING;
        }

        // 6. 域名3天内过期 → 发送即将过期警告
        if (domainExpiringWithin3Days) {
            long remainDays = ChronoUnit.DAYS.between(LocalDateTime.now(), domainExpiry);
            sendNotice(ownerId, domain,
                    "域名即将过期",
                    "您的域名【" + domain.getName() + "】将在" + remainDays + "天后过期，请及时续期。");
            return DomainCheckResult.EXPIRY_WARNING;
        }

        return certSynced ? DomainCheckResult.SYNCED : DomainCheckResult.OK;
    }

    /**
     * 处理删除流程：通知3次后第4天执行删除
     */
    private DomainCheckResult handleDeletionFlow(TopLevelDomain domain, Long ownerId, String reason) {
        int noticeCount = domain.getDeletionNoticeCount() == null ? 0 : domain.getDeletionNoticeCount();

        if (noticeCount >= MAX_DELETION_NOTICES) {
            log.warn("[DomainExpiryCheck] 域名 {} (id={}) 已通知{}次未处理，执行自动删除。原因：{}",
                    domain.getName(), domain.getId(), noticeCount, reason);
            deleteDomain(domain);
            sendNotice(ownerId, domain,
                    "域名已被自动删除",
                    "您的域名【" + domain.getName() + "】因" + reason + "且连续" + noticeCount + "天未处理，已被系统自动删除。");
            return DomainCheckResult.DELETED;
        }

        int remainingDays = MAX_DELETION_NOTICES - noticeCount;
        domain.setDeletionNoticeCount(noticeCount + 1);
        topLevelDomainRepository.save(domain);

        sendNotice(ownerId, domain,
                "域名即将被删除",
                "您的域名【" + domain.getName() + "】因" + reason + "，如" + remainingDays + "天内未处理，系统将自动删除该域名。"
                        + "（第" + (noticeCount + 1) + "次通知，共通知" + MAX_DELETION_NOTICES + "次）");

        log.info("[DomainExpiryCheck] 域名 {} (id={}) 发送删除预告 ({}/{}), 原因: {}",
                domain.getName(), domain.getId(), noticeCount + 1, MAX_DELETION_NOTICES, reason);

        return DomainCheckResult.DELETION_NOTICE;
    }

    /**
     * 读取磁盘上的实际证书有效期。占位证书视为"证书未设置"，返回 null，
     * 避免占位证书 100 年有效期污染巡检判断与数据库证书有效期字段。
     */
    private LocalDateTime readActualCertExpiry(TopLevelDomain domain) {
        try {
            return SslCertificateUtil.getRealExpiryDate(domain);
        } catch (Exception e) {
            log.debug("[DomainExpiryCheck] 域名 {} 读取证书文件失败: {}", domain.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 将实际证书有效期同步到数据库，返回是否有变更
     */
    private boolean syncCertExpiryToDb(TopLevelDomain domain, LocalDateTime actualExpiry) {
        SSLCertificate sslCert = domain.getSslCertificate();
        LocalDateTime dbExpiry = sslCert != null ? sslCert.getCertificateExpiryDate() : null;

        boolean needSync = false;

        if (actualExpiry == null && dbExpiry != null) {
            // 证书文件不存在，清空数据库记录
            sslCert.setCertificateExpiryDate(null);
            sslCert.setSuccess(false);
            sslCert.setResult("证书文件不存在");
            needSync = true;
        } else if (actualExpiry != null && !actualExpiry.equals(dbExpiry)) {
            if (sslCert == null) {
                sslCert = SSLCertificate.builder()
                        .certificateExpiryDate(actualExpiry)
                        .isCompleted(true)
                        .isSuccess(true)
                        .build();
                domain.setSslCertificate(sslCert);
            } else {
                sslCert.setCertificateExpiryDate(actualExpiry);
            }
            needSync = true;
        }

        if (needSync) {
            topLevelDomainRepository.save(domain);
            log.info("[DomainExpiryCheck] 域名 {} 证书有效期已同步: DB={} → 实际={}", domain.getName(), dbExpiry, actualExpiry);
        }

        return needSync;
    }

    private void sendNotice(Long ownerId, TopLevelDomain domain, String title, String content) {
        if (ownerId == null) {
            log.warn("[DomainExpiryCheck] 域名 {} (id={}) 没有所有人，无法发送通知", domain.getName(), domain.getId());
            return;
        }
        try {
            noticeService.createNotice(title, content, NOTICE_TYPE_DOMAIN, ownerId);
        } catch (Exception e) {
            log.error("[DomainExpiryCheck] 发送通知失败: 域名={}, userId={}", domain.getName(), ownerId, e);
        }
    }

    /**
     * 执行域名删除，与前端删除按钮逻辑一致
     */
    private void deleteDomain(TopLevelDomain domain) {
        try {
            topLevelDomainService.delete(domain.getId());
        } catch (Exception e) {
            log.error("[DomainExpiryCheck] 自动删除域名 {} (id={}) 失败", domain.getName(), domain.getId(), e);
        }
    }

    private Long getOwnerId(TopLevelDomain domain) {
        try {
            return domain.getOwner() != null ? domain.getOwner().getId() : null;
        } catch (Exception e) {
            log.warn("[DomainExpiryCheck] 获取域名 {} (id={}) 所有人失败: {}", domain.getName(), domain.getId(), e.getMessage());
            return null;
        }
    }

    private void setTenantForDomain(TopLevelDomain domain) {
        Long companyId = domain.getCompanyId();
        if (companyId != null) {
            Company company = companyService.companyCached(companyId);
            if (company != null) {
                TenantContext.setCurrentTenant(companyId, company);
            }
        }
    }

    /**
     * 清理本地残留的证书目录和nginx配置文件。
     * 扫描本地文件系统，与有效域名列表比对，删除没有对应有效域名的脏数据。
     */
    private void cleanupOrphanedResources(List<TopLevelDomain> validDomains) {
        Set<String> validCertPaths = validDomains.stream()
                .map(d -> d.getCompanyId() + "/" + d.getName())
                .collect(Collectors.toSet());

        Set<String> validDomainNames = validDomains.stream()
                .map(TopLevelDomain::getName)
                .collect(Collectors.toSet());

        int certCleaned = 0, nginxCleaned = 0;

        // 清理孤立证书目录：/www/certs/{companyId}/{domainName}/
        try {
            File certRoot = new File(CERT_DIR);
            if (certRoot.isDirectory()) {
                File[] companyDirs = certRoot.listFiles(File::isDirectory);
                if (companyDirs != null) {
                    for (File companyDir : companyDirs) {
                        File[] domainDirs = companyDir.listFiles(File::isDirectory);
                        if (domainDirs != null) {
                            for (File domainDir : domainDirs) {
                                String certPath = companyDir.getName() + "/" + domainDir.getName();
                                if (!validCertPaths.contains(certPath)) {
                                    FileUtil.del(domainDir);
                                    certCleaned++;
                                    log.info("[DomainExpiryCheck] 清理孤立证书目录: {}", CERT_DIR + certPath);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[DomainExpiryCheck] 扫描清理孤立证书目录异常", e);
        }

        // 清理孤立nginx配置：/www/nginx/{serverName}/{domainName}.conf
        try {
            for (FrontServer frontServer : frontServerService.listFrontServers()) {
                String serverName = frontServer.getName();
                File serverDir = new File(NGINX_DIR + serverName);
                if (!serverDir.isDirectory()) {
                    continue;
                }

                File[] confFiles = serverDir.listFiles((dir, name) -> name.endsWith(".conf"));
                if (confFiles == null) {
                    continue;
                }

                boolean serverHasCleanup = false;
                for (File confFile : confFiles) {
                    String domainName = confFile.getName().replace(".conf", "");
                    if (!validDomainNames.contains(domainName)) {
                        if (NginxConfigWriter.deleteNginxIfExists(serverName, domainName)) {
                            nginxCleaned++;
                            serverHasCleanup = true;
                            log.info("[DomainExpiryCheck] 清理孤立nginx配置: {}/{}", serverName, confFile.getName());
                        }
                    }
                }

                if (serverHasCleanup) {
                    frontServerService.pushAndRefresh(frontServer.getId());
                }
            }
        } catch (Exception e) {
            log.error("[DomainExpiryCheck] 扫描清理孤立nginx配置异常", e);
        }

        if (certCleaned > 0 || nginxCleaned > 0) {
            log.info("[DomainExpiryCheck] 脏数据清理完成: 证书目录={}, nginx配置={}", certCleaned, nginxCleaned);
        }
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private enum DomainCheckResult {
        OK,
        SYNCED,
        EXPIRY_WARNING,
        EXPIRED_NOTICE,
        DELETION_NOTICE,
        DELETED
    }
}
