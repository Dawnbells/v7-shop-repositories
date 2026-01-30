package cn.v7soft.admin.service.remote;

import cn.v7soft.dao.entities.primary.IpDetailInfo;

public interface IIpApiService {

    IpDetailInfo requestIpDataInfo(String ip);
}
