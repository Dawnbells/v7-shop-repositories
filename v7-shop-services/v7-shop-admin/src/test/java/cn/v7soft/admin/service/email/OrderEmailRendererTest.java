package cn.v7soft.admin.service.email;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEmailRendererTest {

    private final OrderEmailRenderer renderer = new OrderEmailRenderer();

    @Test
    void defaultTemplateContainsOrderCustomerAndDeliveryDetails() {
        OrderEmailRenderer.RenderedOrderEmail email = renderer.render(null, order());

        assertThat(email.subject()).isEqualTo("Order confirmed - #ORDER-1001");
        assertThat(email.content())
                .contains("ORDER CONFIRMED")
                .contains("Thank you, A*** J******!")
                .contains("Classic &lt;Product&gt;")
                .contains("a***@example.com")
                .contains("+1 2**-***-0148")
                .contains("*** M***** S*****")
                .contains("F****** D******, San Francisco, California")
                .contains("94***")
                .doesNotContain("alex@example.com")
                .doesNotContain("123 Market Street")
                .contains("Please leave it at reception.")
                .contains("Order total");
    }

    @Test
    void customTemplateStillOverridesTheDefaultLayout() {
        JSONObject template = new JSONObject()
                .set("subject", "Your order {{order_id}}")
                .set("content", "<p>Hello {{customer_name}}: {{order_amount}}</p>");

        OrderEmailRenderer.RenderedOrderEmail email = renderer.render(template, order());

        assertThat(email.subject()).isEqualTo("Your order ORDER-1001");
        assertThat(email.content())
                .startsWith("<p>Hello A*** J******:")
                .doesNotContain("ORDER CONFIRMED");
    }

    private OrderEmailDto order() {
        return OrderEmailDto.builder()
                .originOrderId("ORDER-1001")
                .firstName("Alex")
                .lastName("Johnson")
                .email("alex@example.com")
                .phone("+1 202-555-0148")
                .address("123 Market Street")
                .district("Financial District")
                .city("San Francisco")
                .province("California")
                .postalCode("94105")
                .remark("Please leave it at reception.")
                .currencyCode("USD")
                .totalAmount(new BigDecimal("89.98"))
                .items(List.of(OrderEmailDto.Item.builder()
                        .specTitle("Classic <Product>")
                        .sellPrice(new BigDecimal("44.99"))
                        .quantity(2L)
                        .build()))
                .build();
    }
}
