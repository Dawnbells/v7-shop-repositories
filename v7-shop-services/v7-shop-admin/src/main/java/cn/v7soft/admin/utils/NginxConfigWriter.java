package cn.v7soft.admin.utils;

import java.util.List;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.enums.NginxConfigType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NginxConfigWriter {

    public static void deleteNginx(String serverName, String domain) {
        String path = "/www/nginx/" + serverName + "/" + domain + ".conf";
        if (FileUtil.exist(path)) {
            FileUtil.del(path);
        }
    }

    public static boolean existsNginxConfig(String serverName, String domain) {
        String path = "/www/nginx/" + serverName + "/" + domain + ".conf";
        return FileUtil.exist(path);
    }

    public static boolean writeNginx(String serverName, String domain, NginxConfigType nginxConfigType, String companyId) {
        String path = "/www/nginx/" + serverName + "/" + domain + ".conf";
        try {
            if (FileUtil.exist(path)) {
                deleteNginx(serverName, domain);
            }
            String nginxConfig = String.format(NginxConfigType.VIKE == nginxConfigType ? NGINX_CONFIG_VIKE : NGINX_CONFIG, domain, companyId);
            FileUtil.writeUtf8String(nginxConfig, path);
            return true;
        } catch (Exception e) {
            log.error("write nginx error", e);
            ServiceResponseEnum.ERR_WRITE_NGINX_CONF.throwException(path);
            return false;
        }
    }

    private static final String NGINX_CONFIG_VIKE = """
            server {
                listen 80;
                server_name %1$s *.%1$s;
                return 301 https://$host$request_uri;
            }
            
            # HTTPS 配置
            server {
                listen 443 ssl;
                http2 on;
                server_name %1$s *.%1$s;
                root /vhost/v7-shop-mallix;
                index index.html;
            
                # SSL 证书配置
                ssl_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_certificate_key /www/certs/%2$s/%1$s/privkey.pem;
                ssl_trusted_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_protocols TLSv1.3 TLSv1.2;
                ssl_prefer_server_ciphers on;
                ssl_ciphers "DEFAULT:@SECLEVEL=1";
                ssl_session_cache shared:SSL:10m;
                ssl_session_timeout 10m;
                ssl_session_tickets off;
            
                # 安全头部
                add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                add_header X-Content-Type-Options nosniff always;
                add_header X-XSS-Protection "1; mode=block" always;
                add_header X-Frame-Options SAMEORIGIN always;
                add_header Referrer-Policy no-referrer-when-downgrade always;
                add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
            
                location /builder {
                    proxy_pass http://xyzdwd-mall-service;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_hide_header X-Frame-Options;
                    add_header Content-Security-Policy "frame-ancestors 'self' *.%1$s %1$s" always;
                    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                    add_header X-Content-Type-Options nosniff always;
                }
            
                location /static/ {
                    proxy_pass http://xyzdwd-mall-service;
                    proxy_set_header Host $host;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header Connection "keep-alive";
            
                    # 启用缓存
                    proxy_cache IMAGE_CACHE;
                    proxy_cache_key "$scheme$host$request_uri";
                    proxy_cache_valid 200 301 302 30d;
            
                    # 添加缓存状态响应头
                    add_header X-Cache-Status $upstream_cache_status;
            
                    # 浏览器端缓存控制
                    expires 1y;
                    add_header Cache-Control "public, max-age=31536000, immutable";
                }
            
                location / {
                    proxy_pass http://xyzdwd-mall-service;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                }
            
                # 日志
                access_log /var/log/nginx/%1$s.access.log main;
                error_log /var/log/nginx/%1$s.error.log warn;
            }
            """;

    private static final String NGINX_CONFIG = """
            # HTTP 重定向到 HTTPS
            server {
                listen 80;
                server_name %1$s *.%1$s;
                return 301 https://$host$request_uri;
            }
            
            # HTTPS 配置
            server {
                listen 443 ssl;
                http2 on;
                server_name %1$s *.%1$s;
                root /vhost/v7-shop-mallix;
                index index.html;
            
                # SSL 证书配置
                ssl_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_certificate_key /www/certs/%2$s/%1$s/privkey.pem;
                ssl_trusted_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_protocols TLSv1.3 TLSv1.2;
                ssl_prefer_server_ciphers on;
                ssl_ciphers "DEFAULT:@SECLEVEL=1";
                ssl_session_cache shared:SSL:10m;
                ssl_session_timeout 10m;
                ssl_session_tickets off;
            
                # 安全头部
                add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                add_header X-Content-Type-Options nosniff always;
                add_header X-XSS-Protection "1; mode=block" always;
                add_header X-Frame-Options SAMEORIGIN always;
                add_header Referrer-Policy no-referrer-when-downgrade always;
                add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
            
                location /builder {
                    proxy_pass http://xyzdwd-frontend-service;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_hide_header X-Frame-Options;
                    add_header Content-Security-Policy "frame-ancestors 'self' *.%1$s %1$s" always;
                    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                    add_header X-Content-Type-Options nosniff always;
                }
            
                location /static/ {
                    proxy_pass http://xyzdwd-frontend-service;
                    proxy_set_header Host $host;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header Connection "keep-alive";
            
                    # 启用缓存
                    proxy_cache IMAGE_CACHE;
                    proxy_cache_key "$scheme$host$request_uri";
                    proxy_cache_valid 200 301 302 30d;
            
                    # 添加缓存状态响应头
                    add_header X-Cache-Status $upstream_cache_status;
            
                    # 浏览器端缓存控制
                    expires 1y;
                    add_header Cache-Control "public, max-age=31536000, immutable";
                }
            
                location / {
                    proxy_pass http://xyzdwd-frontend-service;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                }
            
                # 日志
                access_log /var/log/nginx/%1$s.access.log main;
                error_log /var/log/nginx/%1$s.error.log warn;
            }
            """;

    public static void main(String[] args) {
        List<String> domains = FileUtil.listFileNames("E:\\V7Soft\\Repositories\\v7-shop\\sources\\dwd-sync\\nginx\\prod-xyz-fsn-01");

        for (String domainConfName : domains) {
            String path = "E:\\V7Soft\\Repositories\\v7-shop\\sources\\dwd-sync\\nginx\\prod-xyz-fsn-01\\" + domainConfName;
            String nginxConfig = String.format(NGINX_CONFIG, domainConfName.replaceAll(".conf", ""), 1);
            FileUtil.writeUtf8String(nginxConfig, path);
        }
    }
}
