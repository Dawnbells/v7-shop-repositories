package cn.v7soft.dao.repositories.primary;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.ShopifyWebhookEvent;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopifyWebhookEventRepository extends BaseRepository<ShopifyWebhookEvent> {

    /**
     * 按 X-Shopify-Webhook-Id 去重
     */
    boolean existsByWebhookId(String webhookId);

    /**
     * 取到期待处理的最早 50 条事件
     */
    List<ShopifyWebhookEvent> findTop50ByStateAndNextAttemptAtBeforeOrderByReceivedAtAsc(ShopifyWebhookEvent.State state,
                                                                                        LocalDateTime time);
}
