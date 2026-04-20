package cn.v7soft.admin.utils;

import cn.hutool.core.io.FileUtil;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.enums.NginxConfigType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NginxConfigWriter {

    public static void deleteNginx(String serverName, String domain) {
        String path = buildPath(serverName, domain);
        FileUtil.del(path);
    }

    public static boolean existsNginxConfig(String serverName, String domain) {
        return FileUtil.exist(buildPath(serverName, domain));
    }

    public static boolean writeNginx(String serverName, String domain, NginxConfigType nginxConfigType, String companyId) {
        String path = buildPath(serverName, domain);
        try {
            FileUtil.del(path);
            String template = nginxConfigType == NginxConfigType.NUXT_MALL ? NGINX_CONFIG_NUXT_TEMPLATE : NGINX_CONFIG_TEMPLATE;
            String nginxConfig = String.format(template, domain, companyId, nginxConfigType.getUpstream());
            FileUtil.writeUtf8String(nginxConfig, path);
            return true;
        } catch (Exception e) {
            log.error("write nginx error", e);
            ServiceResponseEnum.ERR_WRITE_NGINX_CONF.throwException(path);
            return false;
        }
    }

    private static String buildPath(String serverName, String domain) {
        return "/www/nginx/" + serverName + "/" + domain + ".conf";
    }

    private static final String NGINX_CONFIG_TEMPLATE = """
            server {
                listen 80;
                server_name %1$s *.%1$s;
                return 301 https://$host$request_uri;
            }
            
            server {
                listen 443 ssl;
                http2 on;
                server_name %1$s *.%1$s;
                root /vhost/v7-shop-mallix;
                index index.html;
            
                ssl_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_certificate_key /www/certs/%2$s/%1$s/privkey.pem;
                ssl_trusted_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_protocols TLSv1.3 TLSv1.2;
                ssl_prefer_server_ciphers on;
                ssl_ciphers "DEFAULT:@SECLEVEL=1";
                ssl_session_cache shared:SSL:10m;
                ssl_session_timeout 10m;
                ssl_session_tickets off;
            
                add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                add_header X-Content-Type-Options nosniff always;
                add_header X-XSS-Protection "1; mode=block" always;
                add_header X-Frame-Options SAMEORIGIN always;
                add_header Referrer-Policy no-referrer-when-downgrade always;
                add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
            
                location /static/ {
                    proxy_pass http://%3$s;
                    proxy_set_header Host $host;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header Connection "keep-alive";
            
                    proxy_cache IMAGE_CACHE;
                    proxy_cache_key "$scheme$host$request_uri";
                    proxy_cache_valid 200 301 302 30d;
            
                    add_header X-Cache-Status $upstream_cache_status;
            
                    expires 1y;
                    add_header Cache-Control "public, max-age=31536000, immutable";
                }
            
                location / {
                    proxy_pass http://%3$s;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                }
            
                access_log /var/log/nginx/%1$s.access.log main;
                error_log /var/log/nginx/%1$s.error.log warn;
            }
            """;

    private static final String NGINX_CONFIG_NUXT_TEMPLATE = """
            server {
                listen 80;
                server_name %1$s *.%1$s;
                return 301 https://$host$request_uri;
            }
            
            server {
                listen 443 ssl;
                http2 on;
                server_name %1$s *.%1$s;
            
                ssl_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_certificate_key /www/certs/%2$s/%1$s/privkey.pem;
                ssl_trusted_certificate /www/certs/%2$s/%1$s/fullchain.pem;
                ssl_protocols TLSv1.3 TLSv1.2;
                ssl_prefer_server_ciphers on;
                ssl_ciphers "DEFAULT:@SECLEVEL=1";
                ssl_session_cache shared:SSL:10m;
                ssl_session_timeout 10m;
                ssl_session_tickets off;
            
                add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
                add_header X-Content-Type-Options nosniff always;
                add_header X-Frame-Options SAMEORIGIN always;
                add_header Referrer-Policy no-referrer-when-downgrade always;
                add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
            
                location /_nuxt/ {
                    proxy_pass http://%3$s;
                    proxy_http_version 1.1;
                    proxy_set_header Connection "";
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            
                    proxy_cache NUXT_CACHE;
                    proxy_cache_key "$scheme$host$request_uri";
                    proxy_cache_valid 200 365d;
                    proxy_cache_use_stale error timeout updating;
            
                    add_header X-Cache-Status $upstream_cache_status;
                    expires 1y;
                    add_header Cache-Control "public, max-age=31536000, immutable";
                }
            
                location / {
                    proxy_pass http://%3$s;
                    proxy_http_version 1.1;
                    proxy_set_header Connection "";
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_set_header X-Forwarded-Ssl on;
            
                    proxy_buffering on;
                    proxy_buffer_size 16k;
                    proxy_buffers 8 16k;
            
                    proxy_no_cache 1;
                    proxy_cache_bypass 1;
                }
            
                access_log /var/log/nginx/%1$s.access.log main;
                error_log /var/log/nginx/%1$s.error.log warn;
            }
            """;
}
