package cn.v7soft.admin.service.dns.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.utils.IpUtils;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GoDaddyDnsService implements IDnsService {
    private static final String DEFAULT_ENDPOINT = "https://api.godaddy.com";
    private static final int DEFAULT_TTL = 600;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CloudPlatform getPlatform() {
        return CloudPlatform.GODADDY;
    }

    @Override
    public boolean updateRecord(CloudPlatformAccount account, String domainName, String subName, String recordValue) {
        try {
            String type = IpUtils.isIp(recordValue) ? "A" : "CNAME";
            DnsRecord exactRecord = firstRecord(account, domainName, subName);
            DnsRecord wildcardRecord = firstRecord(account, domainName, "*");

            if (exactRecord != null && recordValue.equals(exactRecord.value())) {
                return false;
            }
            if (exactRecord == null && wildcardRecord != null && recordValue.equals(wildcardRecord.value())) {
                return false;
            }
            if (exactRecord != null && !type.equalsIgnoreCase(exactRecord.type())) {
                deleteRecordByType(account, domainName, subName, exactRecord.type());
            }
            return putRecord(account, domainName, type, subName, recordValue);
        } catch (Exception e) {
            log.error("GoDaddy DNS update failed: {}.{} -> {}", subName, domainName, recordValue, e);
            return false;
        }
    }

    @Override
    public boolean deleteRecord(CloudPlatformAccount account, String domainName, String subName) {
        try {
            deleteRecordByType(account, domainName, subName, "A");
            deleteRecordByType(account, domainName, subName, "CNAME");
            return true;
        } catch (Exception e) {
            log.error("GoDaddy DNS delete failed: {}.{}", subName, domainName, e);
            return false;
        }
    }

    @Override
    public String queryRecord(CloudPlatformAccount account, String domainName, String subName) {
        try {
            DnsRecord exactRecord = firstRecord(account, domainName, subName);
            if (exactRecord != null) {
                return exactRecord.value();
            }
            DnsRecord wildcardRecord = firstRecord(account, domainName, "*");
            return wildcardRecord == null ? null : wildcardRecord.value();
        } catch (Exception e) {
            log.error("GoDaddy DNS query failed: {}.{}", subName, domainName, e);
            return null;
        }
    }

    @Override
    public LocalDateTime queryDomainExpiryDate(CloudPlatformAccount account, String domainName) {
        try {
            HttpRequest request = requestBuilder(account, "/v1/domains/" + encode(domainName))
                    .GET()
                    .build();
            HttpResponse<String> response = send(request);
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode expires = body.get("expires");
            if (expires != null && StrUtil.isNotBlank(expires.asText())) {
                return OffsetDateTime.parse(expires.asText()).toLocalDateTime();
            }
        } catch (Exception e) {
            log.error("GoDaddy domain expiry query failed: {}", domainName, e);
        }
        return null;
    }

    private DnsRecord firstRecord(CloudPlatformAccount account, String domainName, String name) throws Exception {
        List<DnsRecord> records = new ArrayList<>();
        records.addAll(listRecords(account, domainName, "A", name));
        records.addAll(listRecords(account, domainName, "CNAME", name));
        return records.isEmpty() ? null : records.get(0);
    }

    private List<DnsRecord> listRecords(CloudPlatformAccount account, String domainName, String type, String name) throws Exception {
        HttpRequest request = requestBuilder(account, "/v1/domains/" + encode(domainName) + "/records/" + type + "/" + encode(name))
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        JsonNode nodes = objectMapper.readTree(response.body());
        List<DnsRecord> records = new ArrayList<>();
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                JsonNode data = node.get("data");
                if (data != null && StrUtil.isNotBlank(data.asText())) {
                    records.add(new DnsRecord(type, data.asText()));
                }
            }
        }
        return records;
    }

    private boolean putRecord(CloudPlatformAccount account, String domainName, String type, String name, String value) throws Exception {
        String body = objectMapper.writeValueAsString(List.of(new GoDaddyRecordValue(value, DEFAULT_TTL)));
        HttpRequest request = requestBuilder(account, "/v1/domains/" + encode(domainName) + "/records/" + type + "/" + encode(name))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        send(request);
        return true;
    }

    private void deleteRecordByType(CloudPlatformAccount account, String domainName, String name, String type) throws Exception {
        HttpRequest request = requestBuilder(account, "/v1/domains/" + encode(domainName) + "/records/" + type + "/" + encode(name))
                .DELETE()
                .build();
        send(request);
    }

    private HttpRequest.Builder requestBuilder(CloudPlatformAccount account, String path) {
        return HttpRequest.newBuilder(URI.create(endpoint(account) + path))
                .header("Authorization", "sso-key " + account.getAccessKey() + ":" + account.getAccessKeySecret())
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response;
    }

    private String endpoint(CloudPlatformAccount account) {
        return StrUtil.blankToDefault(account.getEndpoint(), DEFAULT_ENDPOINT).replaceAll("/+$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record DnsRecord(String type, String value) {
    }

    private record GoDaddyRecordValue(String data, int ttl) {
    }
}
