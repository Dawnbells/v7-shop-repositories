package cn.v7soft.common.forest.address;

import com.dtflys.forest.callback.AddressSource;
import com.dtflys.forest.http.ForestAddress;
import com.dtflys.forest.http.ForestRequest;

public class RemoteAddress implements AddressSource {
    @Override
    public ForestAddress getAddress(ForestRequest forestRequest) {
        // 返回 Forest 地址对象
        return new ForestAddress("api.v7soft.cn", 13900);
    }
}
