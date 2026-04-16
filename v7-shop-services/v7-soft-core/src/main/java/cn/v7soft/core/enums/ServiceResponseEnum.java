package cn.v7soft.core.enums;

import cn.v7soft.core.asserts.IClientExceptionAssert;
import lombok.Getter;

@Getter
public enum ServiceResponseEnum implements IClientExceptionAssert {
    AA(200, "", ""),
    NOT_CONVERTER(200, "1000", "未提供转换器"),
    ERR_MKDIR(200, "1001", "文件夹创建失败: {0}"),
    ERR_UPLOAD(200, "1002", "文件上传失败: {0}"),
    ERR_OSS_CONFIG(200, "1003", "阿里云OSS未配置: {0}"),
    ERR_IP_API_CONFIG(200, "2001", "第三方IP接口未配置: {0}"),
    ERR_UNKNOWN_LANGUAGE_CONFIG(200, "3001", "未配置语言包: {0}"),
    ERR_S3_CONFIG(200, "3002", "请检查S3存储配置"),
    ERR_THUMBNAILS(200, "3002", "图片变化失败"),
    ERR_CONVERT_WEBP(200, "3002", "转换WebP失败"),
    ERR_ROUTER_TREE(200, "3002", "路由依赖递归"),
    ERR_ENCRYPT(200, "3003", "签名失败: {0}"),
    ERR_DECRYPT(200, "3003", "验签失败: {0}"),
    UNSUPPORTED(200, "4001", "暂不支持: {0}"),
    ERR_WRITE_NGINX_CONF(200, "5001", "NGINX配置写入失败: {0}"),
    ERR_NO_SSL(200, "5002", "证书未部署，请先部署证书绑定。"),
    ERR_FORBIDDEN(403, "403", "Forbidden"),
    ERR_UNREADY(200, "6001", "服务未准备好"),
    ERR_TOKEN_EMPTY(200, "7001", "第三方商城未获取Token: {0}"),
    ERR_TOKEN_INVALID(200, "7002", "第三方商城Token已失效: {0}"),
    ERR_NO_LANGUAGE(200, "8001", "未配置语言包"),
    NOT_FOUND(404, "404", "Not Found"),
    UNKNOWN(200, "99999", "未知错误");

    private final int status;
    private final String code;
    private final String message;

    ServiceResponseEnum(int status, String code, String msg) {
        this.status = status;
        this.code = code;
        this.message = msg;
    }

    @Override
    public String getRealCode() {
        return "S" + code;
    }
}
