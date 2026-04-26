package cn.v7soft.admin.service.dns.impl;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.admin.utils.IpUtils;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NamecheapDnsService implements IDnsService {
    private static final String API_ENDPOINT = "https://api.namecheap.com/xml.response";
    private static final String DEFAULT_TTL = "600";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public CloudPlatform getPlatform() {
        return CloudPlatform.NAMECHEAP;
    }

    @Override
    public boolean updateRecord(CloudPlatformAccount account, String domainName, String subName, String recordValue) {
        try {
            String type = IpUtils.isIp(recordValue) ? "A" : "CNAME";
            List<NamecheapHost> hosts = getHosts(account, domainName);
            NamecheapHost exactRecord = firstHost(hosts, subName);
            NamecheapHost wildcardRecord = firstHost(hosts, "*");

            if (exactRecord != null && recordValue.equals(exactRecord.address())) {
                return false;
            }
            if (exactRecord == null && wildcardRecord != null && recordValue.equals(wildcardRecord.address())) {
                return false;
            }

            List<NamecheapHost> nextHosts = hosts.stream()
                    .filter(host -> !(subName.equalsIgnoreCase(host.name())
                                      && ("A".equalsIgnoreCase(host.type()) || "CNAME".equalsIgnoreCase(host.type()))))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            nextHosts.add(new NamecheapHost(subName, type, recordValue, "", DEFAULT_TTL));
            setHosts(account, domainName, nextHosts);
            return true;
        } catch (Exception e) {
            log.error("Namecheap DNS update failed: {}.{} -> {}", subName, domainName, recordValue, e);
            return false;
        }
    }

    @Override
    public boolean deleteRecord(CloudPlatformAccount account, String domainName, String subName) {
        try {
            List<NamecheapHost> hosts = getHosts(account, domainName);
            List<NamecheapHost> nextHosts = hosts.stream()
                    .filter(host -> !(subName.equalsIgnoreCase(host.name())
                                      && ("A".equalsIgnoreCase(host.type()) || "CNAME".equalsIgnoreCase(host.type()))))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            if (nextHosts.size() == hosts.size()) {
                return true;
            }
            setHosts(account, domainName, nextHosts);
            return true;
        } catch (Exception e) {
            log.error("Namecheap DNS delete failed: {}.{}", subName, domainName, e);
            return false;
        }
    }

    @Override
    public String queryRecord(CloudPlatformAccount account, String domainName, String subName) {
        try {
            List<NamecheapHost> hosts = getHosts(account, domainName);
            NamecheapHost exactRecord = firstHost(hosts, subName);
            if (exactRecord != null) {
                return exactRecord.address();
            }
            NamecheapHost wildcardRecord = firstHost(hosts, "*");
            return wildcardRecord == null ? null : wildcardRecord.address();
        } catch (Exception e) {
            log.error("Namecheap DNS query failed: {}.{}", subName, domainName, e);
            return null;
        }
    }

    @Override
    public LocalDateTime queryDomainExpiryDate(CloudPlatformAccount account, String domainName) {
        try {
            Document document = call(account, "namecheap.domains.getInfo", domainParams(account, domainName));
            NodeList nodes = document.getElementsByTagName("DomainDetails");
            if (nodes.getLength() == 0) {
                return null;
            }
            Element details = (Element) nodes.item(0);
            String expiredDate = text(details, "ExpiredDate");
            if (StrUtil.isBlank(expiredDate)) {
                return null;
            }
            return parseNamecheapDate(expiredDate);
        } catch (Exception e) {
            log.error("Namecheap domain expiry query failed: {}", domainName, e);
            return null;
        }
    }

    private List<NamecheapHost> getHosts(CloudPlatformAccount account, String domainName) throws Exception {
        DomainParts parts = splitDomain(domainName);
        Document document = call(account, "namecheap.domains.dns.getHosts",
                "&SLD=" + encode(parts.sld()) + "&TLD=" + encode(parts.tld()));
        NodeList nodes = document.getElementsByTagName("Host");
        if (nodes.getLength() == 0) {
            nodes = document.getElementsByTagName("host");
        }
        List<NamecheapHost> hosts = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element host = (Element) nodes.item(i);
            String name = host.getAttribute("Name");
            String type = host.getAttribute("Type");
            String address = host.getAttribute("Address");
            if (StrUtil.isBlank(name) || StrUtil.isBlank(type) || StrUtil.isBlank(address)) {
                continue;
            }
            hosts.add(new NamecheapHost(
                    name,
                    type,
                    address,
                    host.getAttribute("MXPref"),
                    StrUtil.blankToDefault(host.getAttribute("TTL"), DEFAULT_TTL)
            ));
        }
        return hosts;
    }

    private void setHosts(CloudPlatformAccount account, String domainName, List<NamecheapHost> hosts) throws Exception {
        DomainParts parts = splitDomain(domainName);
        StringBuilder params = new StringBuilder()
                .append("&SLD=").append(encode(parts.sld()))
                .append("&TLD=").append(encode(parts.tld()));
        for (int i = 0; i < hosts.size(); i++) {
            int index = i + 1;
            NamecheapHost host = hosts.get(i);
            params.append("&HostName").append(index).append("=").append(encode(host.name()))
                    .append("&RecordType").append(index).append("=").append(encode(host.type()))
                    .append("&Address").append(index).append("=").append(encode(host.address()))
                    .append("&TTL").append(index).append("=").append(encode(StrUtil.blankToDefault(host.ttl(), DEFAULT_TTL)));
            if (StrUtil.isNotBlank(host.mxPref())) {
                params.append("&MXPref").append(index).append("=").append(encode(host.mxPref()));
            }
        }
        call(account, "namecheap.domains.dns.setHosts", params.toString());
    }

    private NamecheapHost firstHost(List<NamecheapHost> hosts, String name) {
        return hosts.stream()
                .filter(host -> name.equalsIgnoreCase(host.name()))
                .filter(host -> "A".equalsIgnoreCase(host.type()) || "CNAME".equalsIgnoreCase(host.type()))
                .findFirst()
                .orElse(null);
    }

    private Document call(CloudPlatformAccount account, String command, String params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(account, command, params)))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        Document document = parseXml(response.body());
        Element root = document.getDocumentElement();
        if (!"OK".equalsIgnoreCase(root.getAttribute("Status"))) {
            throw new IllegalStateException(response.body());
        }
        return document;
    }

    private String endpoint(CloudPlatformAccount account, String command, String params) {
        return API_ENDPOINT
                + "?ApiUser=" + encode(account.getAccessKey())
                + "&ApiKey=" + encode(account.getAccessKeySecret())
                + "&UserName=" + encode(account.getAccessKey())
                + "&ClientIp=" + encode(account.getEndpoint())
                + "&Command=" + encode(command)
                + params;
    }

    private String domainParams(CloudPlatformAccount account, String domainName) {
        return "&DomainName=" + encode(domainName);
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private LocalDateTime parseNamecheapDate(String date) {
        String value = date.trim();
        List<DateTimeFormatter> dateTimeFormatters = List.of(
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US),
                DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm:ss a", Locale.US)
        );
        for (DateTimeFormatter formatter : dateTimeFormatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ignored) {
            }
        }
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
        return LocalDate.parse(value, dateFormatter).atStartOfDay();
    }

    private DomainParts splitDomain(String domainName) {
        String cleanDomain = domainName == null ? "" : domainName.trim();
        int dotIndex = cleanDomain.indexOf('.');
        if (dotIndex <= 0 || dotIndex == cleanDomain.length() - 1) {
            throw new IllegalArgumentException("Invalid domain: " + domainName);
        }
        return new DomainParts(cleanDomain.substring(0, dotIndex), cleanDomain.substring(dotIndex + 1));
    }

    private String encode(String value) {
        return URLEncoder.encode(StrUtil.blankToDefault(value, ""), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record NamecheapHost(String name, String type, String address, String mxPref, String ttl) {
    }

    private record DomainParts(String sld, String tld) {
    }
}
