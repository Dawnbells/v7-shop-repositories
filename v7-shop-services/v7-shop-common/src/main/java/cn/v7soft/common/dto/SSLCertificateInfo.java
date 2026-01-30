package cn.v7soft.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * SSL 证书信息实体类
 */
@Data
@Builder
public class SSLCertificateInfo {

    /**
     * 证书的公用名称（Common Name）
     */
    @JsonProperty("common_name")
    private String commonName;

    /**
     * 证书签发机构的通用名称
     */
    @JsonProperty("i_common_name")
    private String iCommonName;

    /**
     * 证书签发机构的国家名称
     */
    @JsonProperty("i_country_name")
    private String iCountryName;
    /**
     * 证书颁发机构名称
     */
    @JsonProperty("cert_ca_not")
    private String certCaNot;

    /**
     * 证书序列号
     */
    @JsonProperty("cert_serial_number")
    private String certSerialNumber;

    /**
     * 证书类型（如 DV 证书）
     */
    @JsonProperty("cert_belong")
    private String certBelong;

    /**
     * 证书适用的角色（如 serverAuth, clientAuth）
     */
    @JsonProperty("cert_for_who")
    private String certForWho;

    /**
     * 公钥类型（如 ECDSA）
     */
    @JsonProperty("public_key")
    private String publicKey;

    /**
     * 签名算法（如 SHA384）
     */
    @JsonProperty("sign_with")
    private String signWith;

    /**
     * 证书有效期开始时间
     */
    @JsonProperty("cert_not_valid_before")
    private String certNotValidBefore;

    /**
     * 证书有效期结束时间
     */
    @JsonProperty("cert_not_valid_after")
    private String certNotValidAfter;

    /**
     * 证书有效天数
     */
    @JsonProperty("cert_valid_days")
    private int certValidDays;

    /**
     * 证书 SHA-1 哈希值
     */
    @JsonProperty("cert_hash_sha1")
    private String certHashSha1;

    /**
     * 证书 SHA-256 哈希值
     */
    @JsonProperty("cert_hash_sha256")
    private String certHashSha256;

    /**
     * 证书签名
     */
    @JsonProperty("cer_signature")
    private String cerSignature;

    /**
     * 证书适用的域名
     */
    @JsonProperty("cert_extent_info")
    private String certExtentInfo;

    /**
     * 公钥字符串
     */
    @JsonProperty("public_key_string")
    private String publicKeyString;
}
