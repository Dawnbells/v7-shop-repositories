package cn.v7soft.admin.service.frontagent;

import cn.v7soft.dao.entities.primary.FrontAgentReport;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.NginxConfigType;
import cn.v7soft.dao.repositories.primary.FrontAgentReportRepository;
import cn.v7soft.dao.repositories.primary.TopLevelDomainRepository;
import cn.v7soft.dao.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrontAgentManifestServiceTest {

    @TempDir
    Path certsDir;

    @Mock
    TopLevelDomainRepository topLevelDomainRepository;

    @Mock
    FrontAgentReportRepository frontAgentReportRepository;

    private FrontAgentProperties properties;
    private FrontAgentManifestService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final long COMPANY_ID = 5L;

    @BeforeEach
    void setUp() {
        properties = new FrontAgentProperties();
        properties.setCertsDir(certsDir.toString());
        properties.setManifestCacheMillis(0); // 关闭快照缓存，保证每次断言都走真实构建
        properties.setServices(Map.of(NginxConfigType.NUXT_MALL, List.of("127.0.0.1:3000")));
        service = new FrontAgentManifestService(
                topLevelDomainRepository, frontAgentReportRepository, properties, new CertFingerprintCache());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TopLevelDomain domain(String name, NginxConfigType type) {
        return TopLevelDomain.builder().name(name).nginxConfigType(type).companyId(COMPANY_ID).build();
    }

    private void writeCert(String domainName) throws IOException {
        Path dir = certsDir.resolve(String.valueOf(COMPANY_ID)).resolve(domainName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("fullchain.pem"), "cert-of-" + domainName);
        Files.writeString(dir.resolve("privkey.pem"), "key-of-" + domainName);
    }

    @Test
    @DisplayName("manifest: 同数据同版本（确定性），域名按名称排序，version 与 body 一致")
    void manifestIsDeterministicAndSorted() throws IOException {
        writeCert("a.com");
        writeCert("b.com");
        when(topLevelDomainRepository.findAllAgentServableDomains())
                .thenReturn(List.of(domain("b.com", NginxConfigType.NUXT_MALL), domain("a.com", NginxConfigType.NUXT_MALL)));

        FrontAgentManifestService.ManifestSnapshot first = service.getManifest();
        FrontAgentManifestService.ManifestSnapshot second = service.getManifest();

        assertEquals(first.version(), second.version(), "同数据必须产生相同版本号");
        assertTrue(first.version().startsWith("sha256:"));

        JsonNode body = mapper.readTree(first.body());
        assertEquals(first.version(), body.get("version").asText(), "body 内的 version 必须与快照一致");
        assertEquals("a.com", body.get("domains").get(0).get("domain").asText(), "域名必须按名称排序");
        assertEquals("b.com", body.get("domains").get(1).get("domain").asText());
        assertEquals("127.0.0.1:3000", body.get("services").get("NUXT_MALL").get(0).asText());
        assertNotNull(body.get("domains").get(0).get("fullchainSha256"));
        assertNotNull(body.get("domains").get(0).get("privkeySha256"));
    }

    @Test
    @DisplayName("manifest: 缺少证书文件的域名被跳过；serviceType 为空时回退 THYMELEAF")
    void manifestSkipsDomainsWithoutCertAndDefaultsServiceType() throws IOException {
        writeCert("ok.com");
        // missing.com 不写证书文件
        when(topLevelDomainRepository.findAllAgentServableDomains())
                .thenReturn(List.of(domain("ok.com", null), domain("missing.com", NginxConfigType.NUXT_MALL)));

        JsonNode body = mapper.readTree(service.getManifest().body());

        assertEquals(1, body.get("domains").size(), "缺证书的域名不应进入 manifest");
        assertEquals("ok.com", body.get("domains").get(0).get("domain").asText());
        assertEquals("THYMELEAF", body.get("domains").get(0).get("serviceType").asText(),
                "与旧 NginxConfigWriter 的默认值保持一致");
    }

    @Test
    @DisplayName("manifest: 证书内容变化 → 版本号变化（agent 304 短路被打破）")
    void manifestVersionChangesWhenCertRotated() throws IOException {
        writeCert("a.com");
        when(topLevelDomainRepository.findAllAgentServableDomains())
                .thenReturn(List.of(domain("a.com", NginxConfigType.NUXT_MALL)));
        String before = service.getManifest().version();

        Path key = certsDir.resolve(String.valueOf(COMPANY_ID)).resolve("a.com").resolve("privkey.pem");
        Files.writeString(key, "renewed-key");
        Files.setLastModifiedTime(key, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 10_000));

        String after = service.getManifest().version();
        org.junit.jupiter.api.Assertions.assertNotEquals(before, after);
    }

    @Test
    @DisplayName("report: 首次上报新建记录，再次上报更新同一行（upsert），超长 message 截断")
    void reportUpsertsByAgentName() {
        when(frontAgentReportRepository.findByAgentName("fsn-01")).thenReturn(Optional.empty());
        ArgumentCaptor<FrontAgentReport> captor = ArgumentCaptor.forClass(FrontAgentReport.class);

        service.report("fsn-01", "sha256:abc", "error", "x".repeat(2000));

        verify(frontAgentReportRepository).save(captor.capture());
        FrontAgentReport saved = captor.getValue();
        assertEquals("fsn-01", saved.getAgentName());
        assertEquals("sha256:abc", saved.getAppliedVersion());
        assertEquals("error", saved.getReportStatus());
        assertEquals(1000, saved.getMessage().length());
        assertNotNull(saved.getReportedAt());

        // 已存在 → 更新同一实体
        FrontAgentReport existing = FrontAgentReport.builder().agentName("fsn-01").build();
        when(frontAgentReportRepository.findByAgentName("fsn-01")).thenReturn(Optional.of(existing));
        service.report("fsn-01", "sha256:def", "ok", null);
        verify(frontAgentReportRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("sha256:def", existing.getAppliedVersion());
        assertEquals("ok", existing.getReportStatus());
        assertNull(existing.getMessage());
    }

    @Test
    @DisplayName("report: 落库异常不向上传播（不能影响 manifest 下发）")
    void reportSwallowsPersistenceErrors() {
        when(frontAgentReportRepository.findByAgentName(anyString())).thenThrow(new RuntimeException("db down"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.report("fsn-01", null, null, null));
    }

    @Test
    @DisplayName("resolveCertFile: 白名单/归属/根目录三道闸")
    void resolveCertFileGuards() throws IOException {
        writeCert("ok.com");
        when(topLevelDomainRepository.findValidByName("ok.com"))
                .thenReturn(List.of(domain("ok.com", NginxConfigType.NUXT_MALL)));

        // 正常路径
        Path resolved = service.resolveCertFile("ok.com", "fullchain.pem");
        assertNotNull(resolved);
        assertTrue(resolved.toString().endsWith("fullchain.pem"));

        // 路径穿越/非法字符：连仓库查询都不应发生
        assertNull(service.resolveCertFile("../5/ok.com", "fullchain.pem"));
        assertNull(service.resolveCertFile("ok..com", "fullchain.pem"));
        assertNull(service.resolveCertFile("OK_COM!", "fullchain.pem"));

        // 域名不属于当前公司（查询为空）
        when(topLevelDomainRepository.findValidByName("other.com")).thenReturn(List.of());
        assertNull(service.resolveCertFile("other.com", "fullchain.pem"));

        // 域名存在但文件缺失
        when(topLevelDomainRepository.findValidByName("nofile.com"))
                .thenReturn(List.of(domain("nofile.com", NginxConfigType.NUXT_MALL)));
        assertNull(service.resolveCertFile("nofile.com", "fullchain.pem"));
    }
}
