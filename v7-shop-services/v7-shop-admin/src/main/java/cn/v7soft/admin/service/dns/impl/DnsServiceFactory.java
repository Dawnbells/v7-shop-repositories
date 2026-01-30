package cn.v7soft.admin.service.dns.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import cn.v7soft.admin.service.dns.IDnsService;
import cn.v7soft.dao.enums.CloudPlatform;

@Component
public class DnsServiceFactory {
    private final Map<CloudPlatform, IDnsService> serviceMap = new HashMap<>();

    public DnsServiceFactory(List<IDnsService> services) {
        for (IDnsService service : services) {
            serviceMap.put(service.getPlatform(), service);
        }
    }

    public IDnsService getService(CloudPlatform platform) {
        return serviceMap.get(platform);
    }
}
