package cn.v7soft.dao.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 定义爬虫类型枚举
@Getter
@AllArgsConstructor
public enum CrawlerType {
    TWITTER_BOT("TwitterBot"),
    GOOGLE_BOT("GoogleBot"),
    BING_BOT("BingBot"),
    AHREFS_BOT("AhrefsBot"),
    DUCK_DUCK_GO_BOT("DuckDuckGoBot"),
    FACEBOOK_BOT("FacebookBot"),
    PINGDOM_BOT("PingdomBot"),
    RSS_API_BOT("RssApiBot"),
    STRIPE_WEBHOOK("StripeWebhook"),
    TELEGRAM_BOT("TelegramBot"),
    UPTIME_ROBOT("UptimeRobot"),
    common_crawl_bot("CommonCrawlBot"),
    EMBEDLY_BOT("EmbedlyBot"),
    SEM_RUSH_BOT("SemRushBot"),
    AMAZON_BOT("AmazonBot"),
    SEMANTIC_SCHOLAR_BOT("SemanticScholarBot"),
    SLACK_BOT("SlackBot"),
    APPLE_BOT("Applebot"),
    GOOGLE_BOT_IMAGE("GoogleBotImage"),
    APIS_GOOGLE("APIsGoogle"),
    ARCHIVE_BOT("ArchiveBot"),
    BYTE_SPIDER("ByteSpider"),
    LINKEDIN_BOT("LinkedInBot"),
    ARCHIVE_ORG_BOT("ArchiveOrgBot"),
    DISCORD_BOT("DiscordBot"),
    MASTODON_BOT("MastodonBot"),
    HUBSPOT_BOT("HubSpotBot"),
    YOU_BOT("YouBot"),
    YANDEX_BOT("YandexBot"),
    NEXTCLOUD_BOT("NextCloudBot"),
    PINTEREST_BOT("PinterestBot"),
    SLURP_BOT("SlurpBot"),
    MAIL_RU_BOT("MailRuBot"),
    FACEBOOK_EXTERNAL_HIT("Facebookexternalhit"),
    ZOOM_BOT("ZoomBot"),
    BING_PREVIEW_BOT("BingPreviewBot"),
    WHATSAPP_BOT("WhatsAppBot"),
    ADS_BOT_GOOGLE("AdsBotGoogle"),
    GOOGLE_BOT_MOBILE("GoogleBotMobile"),
    MEDIAPARTNERS_GOOGLE("MediapartnersGoogle"),
    WORDPRESS_BOT("WordPressBot"),
    DUCKDUCK_BOT("DuckDuckBot"),
    BAIDU_SPIDER("BaiduSpider"),
    NONE("NONE");
    private final String ipApiIsValue;

    public static CrawlerType from(String crawler) {
        for (CrawlerType value : values()) {
            if (value.ipApiIsValue.equalsIgnoreCase(crawler)) {
                return value;
            }
        }
        return NONE;
    }
}