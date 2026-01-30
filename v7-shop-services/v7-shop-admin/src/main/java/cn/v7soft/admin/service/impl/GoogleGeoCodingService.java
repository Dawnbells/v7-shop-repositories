package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.IGoogleGeoCodingService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GoogleGeoCodingService implements IGoogleGeoCodingService {
    private static final String GOOGLE_MAPS_API_KEY = "AIzaSyAhWlBHXMyOWvUFRSRWtiSBZj0zNoWF2xg";

    private static final String GOOGLE_MAPS_API_URL = "https://maps.googleapis.com/maps/api/geocode/json?address={address}&key={apiKey}";

    @Override
    public boolean validateAddress(String address) {
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 调用Google Maps API
            String response = restTemplate.getForObject(GOOGLE_MAPS_API_URL, String.class, address, GOOGLE_MAPS_API_KEY);
            System.out.println("address " + address + ", response = " + response);
            JSONObject jsonResponse = new JSONObject(response);

            // 解析响应，查看是否有有效的结果
            String status = jsonResponse.getStr("status");
            if ("OK".equals(status)) {
                return true; // 地址有效
            } else {
                System.out.println("Invalid address: " + status);
                return false; // 地址无效
            }
        } catch (Exception e) {
            System.err.println("Error validating address: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        new GoogleGeoCodingService().validateAddress("Εθνική οδός Κυπαρισσία πύργου");
        new GoogleGeoCodingService().validateAddress("Ευβοίας 209");
    }
}
