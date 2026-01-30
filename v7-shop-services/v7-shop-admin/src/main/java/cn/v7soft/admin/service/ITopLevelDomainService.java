package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.BindPixelsRequest;
import cn.v7soft.admin.controller.req.BindProtocolRequest;
import cn.v7soft.admin.controller.req.GetCertificateReq;
import cn.v7soft.admin.controller.req.TransferUserRequest;
import cn.v7soft.admin.controller.req.UpdateCertificateReq;
import cn.v7soft.admin.controller.resp.GetCertificateResp;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.TopLevelDomain;
import cn.v7soft.dao.enums.NginxConfigType;

import java.util.List;

import org.springframework.scheduling.annotation.Async;

public interface ITopLevelDomainService extends IBaseDataRangeService<TopLevelDomain> {
    /**
     * 获取所有在排队和正在申请证书的域名进行重新申请
     */
    List<TopLevelDomain> findAllQueueOrRequesting();

    /**
     * 转移用户
     */
    void transferUser(TransferUserRequest request);

    /**
     * 获取域名证书信息
     * @param request 请求
     * @return 证书信息
     */
    GetCertificateResp getCertificate(GetCertificateReq request);

    /**
     * 更新域名证书
     * @param request 请求
     * @return 证书信息
     */
    GetCertificateResp updateCertificate(UpdateCertificateReq request);

    /**
     * 绑定协议到域名
     * @param request 绑定请求
     */
    void bindProtocol(BindProtocolRequest request);

    void nginxConfig(Long id, NginxConfigType type);

    @Async("certificateRequestAsyncExecutor")
    void refreshNginxConfig(Long id, NginxConfigType type);

    /**
     * 绑定像素账号到域名
     * @param request 绑定请求
     */
    void bindPixels(BindPixelsRequest request);

}
