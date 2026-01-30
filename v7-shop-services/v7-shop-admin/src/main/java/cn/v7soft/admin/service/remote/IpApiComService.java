package cn.v7soft.admin.service.remote;

import org.springframework.stereotype.Service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.dao.entities.primary.IpDetailInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IpApiComService implements IIpApiService {

    @Override
    public IpDetailInfo requestIpDataInfo(String ip) {
        String response = HttpUtil.get("https://pro.ip-api.com/json/" + ip + "?key=unyIowq2PK6kuOW&fields=536608767");
        JSONObject ipData = JSONUtil.parseObj(response);
        String status = ipData.getStr("status", "false");
        if ("false".equalsIgnoreCase(status)) {
            return null;
        }
        return IpDetailInfo.builder()
                .ip(ipData.getStr("query", ip))
                .country(ipData.getStr("country", "").toUpperCase())
                .countryCode(ipData.getStr("countryCode", "").toUpperCase())
                .latitude(String.valueOf(ipData.getDouble("lat", 0.00)))
                .longitude(String.valueOf(ipData.getDouble("lon", 0.00)))
                .build();
    }
}
