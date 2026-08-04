package cn.v7soft.admin.service.email;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.service.dto.OrderEmailDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OrderEmailRenderer {

    public RenderedOrderEmail render(JSONObject template, OrderEmailDto dto) {
        return new RenderedOrderEmail(buildSubject(template, dto), buildContent(template, dto));
    }

    private String buildSubject(JSONObject template, OrderEmailDto dto) {
        if (template != null && StrUtil.isNotBlank(template.getStr("subject"))) {
            return replaceVariables(template.getStr("subject"), dto);
        }
        return String.format("Order confirmed - #%s", orderId(dto));
    }

    private String buildContent(JSONObject template, OrderEmailDto dto) {
        if (template != null && StrUtil.isNotBlank(template.getStr("content"))) {
            return replaceVariables(template.getStr("content"), dto);
        }
        return buildDefaultContent(dto);
    }

    private String buildDefaultContent(OrderEmailDto dto) {
        String customerName = fullName(dto);
        String escapedOrderId = escapeHtml(orderId(dto));
        String remark = StrUtil.trim(dto.getRemark());
        String remarkSection = StrUtil.isBlank(remark) ? "" : """
                <tr>
                  <td style="padding:0 32px 28px;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#fffbeb;border:1px solid #fde68a;border-radius:10px;">
                      <tr>
                        <td style="padding:14px 16px;font-size:13px;line-height:20px;color:#78350f;">
                          <strong style="display:block;margin-bottom:3px;color:#92400e;">Order note</strong>
                          %s
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.formatted(escapeHtml(remark));

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Order confirmation</title>
                  <style>
                    @media only screen and (max-width: 620px) {
                      .email-shell { width: 100%% !important; }
                      .email-padding { padding-left: 20px !important; padding-right: 20px !important; }
                      .detail-column { display: block !important; width: 100%% !important; }
                      .detail-column + .detail-column { padding-top: 18px !important; }
                      .item-price { display: none !important; }
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background:#f3f6fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#172033;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                    We received order #%s and are getting it ready.
                  </div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="width:100%%;background:#f3f6fa;">
                    <tr>
                      <td align="center" style="padding:32px 12px;">
                        <table role="presentation" class="email-shell" width="640" cellspacing="0" cellpadding="0" style="width:640px;max-width:640px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 8px 28px rgba(23,32,51,.08);">
                          <tr>
                            <td class="email-padding" style="padding:34px 32px;background:#0f766e;color:#ffffff;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="font-size:12px;font-weight:700;letter-spacing:1.8px;color:#ccfbf1;">ORDER CONFIRMED</td>
                                  <td align="right">
                                    <span style="display:inline-block;width:34px;height:34px;line-height:34px;text-align:center;border-radius:50%%;background:#ffffff;color:#0f766e;font-size:20px;font-weight:700;">&#10003;</span>
                                  </td>
                                </tr>
                              </table>
                              <h1 style="margin:20px 0 8px;font-size:28px;line-height:36px;font-weight:700;">Thank you, %s!</h1>
                              <p style="margin:0;font-size:15px;line-height:24px;color:#e6fffb;">We received your order and will let you know when it is on the way.</p>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-padding" style="padding:28px 32px 20px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f0fdfa;border:1px solid #99f6e4;border-radius:10px;">
                                <tr>
                                  <td style="padding:14px 16px;font-size:12px;line-height:18px;color:#52706d;text-transform:uppercase;letter-spacing:.8px;">Order number</td>
                                  <td align="right" style="padding:14px 16px;font-size:15px;line-height:20px;font-weight:700;color:#115e59;">#%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-padding" style="padding:4px 32px 28px;">
                              <h2 style="margin:0 0 14px;font-size:18px;line-height:26px;color:#172033;">Order details</h2>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                                <tr style="background:#f8fafc;">
                                  <th align="left" style="padding:10px 12px;border-bottom:1px solid #e5eaf0;font-size:11px;color:#667085;text-transform:uppercase;letter-spacing:.6px;">Item</th>
                                  <th align="center" style="padding:10px 8px;border-bottom:1px solid #e5eaf0;font-size:11px;color:#667085;text-transform:uppercase;letter-spacing:.6px;">Qty</th>
                                  <th class="item-price" align="right" style="padding:10px 8px;border-bottom:1px solid #e5eaf0;font-size:11px;color:#667085;text-transform:uppercase;letter-spacing:.6px;">Price</th>
                                  <th align="right" style="padding:10px 12px;border-bottom:1px solid #e5eaf0;font-size:11px;color:#667085;text-transform:uppercase;letter-spacing:.6px;">Amount</th>
                                </tr>
                                %s
                                <tr>
                                  <td colspan="3" align="right" style="padding:18px 8px 4px;font-size:14px;font-weight:600;color:#475467;">Order total</td>
                                  <td align="right" style="padding:18px 12px 4px;font-size:18px;font-weight:700;color:#0f766e;white-space:nowrap;">%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td class="email-padding" style="padding:26px 32px;background:#f8fafc;border-top:1px solid #e5eaf0;border-bottom:1px solid #e5eaf0;">
                              <h2 style="margin:0 0 18px;font-size:18px;line-height:26px;color:#172033;">Customer information</h2>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td class="detail-column" width="50%%" valign="top" style="width:50%%;padding-right:16px;">
                                    <div style="margin-bottom:8px;font-size:11px;font-weight:700;color:#667085;text-transform:uppercase;letter-spacing:.7px;">Contact</div>
                                    <div style="font-size:14px;line-height:22px;color:#344054;">%s</div>
                                    <div style="font-size:14px;line-height:22px;color:#344054;">%s</div>
                                    <div style="font-size:14px;line-height:22px;color:#344054;">%s</div>
                                  </td>
                                  <td class="detail-column" width="50%%" valign="top" style="width:50%%;padding-left:16px;">
                                    <div style="margin-bottom:8px;font-size:11px;font-weight:700;color:#667085;text-transform:uppercase;letter-spacing:.7px;">Delivery address</div>
                                    <div style="font-size:14px;line-height:22px;color:#344054;">%s</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          %s
                          <tr>
                            <td class="email-padding" align="center" style="padding:26px 32px 30px;">
                              <p style="margin:0 0 6px;font-size:14px;line-height:22px;color:#475467;">Questions about your order? Reply to this email and our team will be happy to help.</p>
                              <p style="margin:0;font-size:12px;line-height:20px;color:#98a2b3;">This is an automated order confirmation.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escapedOrderId,
                escapeHtml(customerName),
                escapedOrderId,
                buildDefaultItemRows(dto),
                formatCurrency(dto.getTotalAmount(), dto.getCurrencyCode()),
                escapeHtml(customerName),
                valueOrDash(maskEmail(dto.getEmail())),
                valueOrDash(maskPhone(dto.getPhone())),
                deliveryAddress(dto),
                remarkSection);
    }

    private String buildDefaultItemRows(OrderEmailDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return """
                    <tr>
                      <td colspan="4" align="center" style="padding:24px 12px;border-bottom:1px solid #e5eaf0;font-size:14px;color:#667085;">No item details available</td>
                    </tr>
                    """;
        }

        return dto.getItems().stream().map(item -> {
            BigDecimal price = item.getSellPrice() == null ? BigDecimal.ZERO : item.getSellPrice();
            long quantity = item.getQuantity() == null ? 0L : item.getQuantity();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
            return """
                    <tr>
                      <td style="padding:15px 12px;border-bottom:1px solid #e5eaf0;font-size:14px;line-height:20px;font-weight:600;color:#344054;">%s</td>
                      <td align="center" style="padding:15px 8px;border-bottom:1px solid #e5eaf0;font-size:14px;color:#475467;">%d</td>
                      <td class="item-price" align="right" style="padding:15px 8px;border-bottom:1px solid #e5eaf0;font-size:14px;color:#475467;white-space:nowrap;">%s</td>
                      <td align="right" style="padding:15px 12px;border-bottom:1px solid #e5eaf0;font-size:14px;font-weight:600;color:#344054;white-space:nowrap;">%s</td>
                    </tr>
                    """.formatted(
                    valueOrDash(item.getSpecTitle()),
                    quantity,
                    formatCurrency(price, dto.getCurrencyCode()),
                    formatCurrency(subtotal, dto.getCurrencyCode()));
        }).collect(Collectors.joining());
    }

    private String fullName(OrderEmailDto dto) {
        String name = joinNonBlank(" ", dto.getFirstName(), dto.getLastName());
        if (StrUtil.isBlank(name)) {
            return "Customer";
        }
        return java.util.Arrays.stream(name.split("\\s+"))
                .map(this::maskWord)
                .collect(Collectors.joining(" "));
    }

    private String deliveryAddress(OrderEmailDto dto) {
        String region = maskedRegion(dto);
        String address = joinNonBlank("<br>",
                escapedOrEmpty(maskAddress(dto.getAddress())),
                escapedOrEmpty(region),
                escapedOrEmpty(maskPostalCode(dto.getPostalCode())));
        return StrUtil.isBlank(address) ? "&mdash;" : address;
    }

    private String maskedRegion(OrderEmailDto dto) {
        return joinNonBlank(", ", maskAddress(dto.getDistrict()), dto.getCity(), dto.getProvince());
    }

    private String maskWord(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        int visibleCharacters = Character.charCount(value.codePointAt(0));
        int hiddenCharacters = Math.min(value.length() - visibleCharacters, 6);
        return value.substring(0, visibleCharacters) + "*".repeat(Math.max(hiddenCharacters, 1));
    }

    private String maskEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return maskWord(email);
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "";
        }
        long digitCount = phone.chars().filter(Character::isDigit).count();
        if (digitCount <= 4) {
            return "*".repeat(phone.length());
        }
        StringBuilder masked = new StringBuilder(phone.length());
        int digitIndex = 0;
        for (char character : phone.toCharArray()) {
            if (Character.isDigit(character)) {
                boolean visible = digitIndex < 2 || digitIndex >= digitCount - 4;
                masked.append(visible ? character : '*');
                digitIndex++;
            } else {
                masked.append(character);
            }
        }
        return masked.toString();
    }

    private String maskAddress(String address) {
        if (StrUtil.isBlank(address)) {
            return "";
        }
        return java.util.Arrays.stream(address.trim().split("\\s+"))
                .map(part -> part.chars().allMatch(Character::isDigit)
                        ? "*".repeat(part.length())
                        : maskWord(part))
                .collect(Collectors.joining(" "));
    }

    private String maskPostalCode(String postalCode) {
        if (StrUtil.isBlank(postalCode)) {
            return "";
        }
        int visibleLength = Math.min(2, postalCode.length());
        return postalCode.substring(0, visibleLength)
                + "*".repeat(Math.max(postalCode.length() - visibleLength, 1));
    }

    private String joinNonBlank(String delimiter, String... values) {
        return java.util.Arrays.stream(values)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(delimiter));
    }

    private String valueOrDash(String value) {
        return StrUtil.isBlank(value) ? "&mdash;" : escapeHtml(value);
    }

    private String escapedOrEmpty(String value) {
        return StrUtil.isBlank(value) ? "" : escapeHtml(value);
    }

    private String escapeHtml(String value) {
        return StrUtil.nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatCurrency(BigDecimal amount, String currencyCode) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
        if (StrUtil.isNotBlank(currencyCode)) {
            try {
                currencyFormat.setCurrency(Currency.getInstance(currencyCode));
            } catch (IllegalArgumentException ignored) {
                return escapeHtml(currencyCode) + " "
                        + Objects.requireNonNullElse(amount, BigDecimal.ZERO).toPlainString();
            }
        }
        return escapeHtml(currencyFormat.format(Objects.requireNonNullElse(amount, BigDecimal.ZERO)));
    }

    private String replaceVariables(String template, OrderEmailDto dto) {
        String name = fullName(dto);
        String region = maskedRegion(dto);

        BigDecimal totalAmount = dto.getTotalAmount() == null ? BigDecimal.ZERO : dto.getTotalAmount();
        StringBuilder items = new StringBuilder();
        if (dto.getItems() != null) {
            dto.getItems().forEach(item -> {
                BigDecimal price = item.getSellPrice() == null ? BigDecimal.ZERO : item.getSellPrice();
                items.append(item.getSpecTitle())
                        .append("<br/>")
                        .append(formatCurrency(price, dto.getCurrencyCode()))
                        .append(" × ")
                        .append(item.getQuantity())
                        .append("<br/>");
            });
        }

        return template
                .replace("{{customer_name}}", name.trim())
                .replace("{{customer_phone}}", maskPhone(dto.getPhone()))
                .replace("{{customer_email}}", maskEmail(dto.getEmail()))
                .replace("{{customer_address}}", maskAddress(dto.getAddress()))
                .replace("{{customer_region}}", region.trim())
                .replace("{{customer_postal_code}}", maskPostalCode(dto.getPostalCode()))
                .replace("{{customer_remark}}", StrUtil.nullToEmpty(dto.getRemark()))
                .replace("{{order_id}}", orderId(dto))
                .replace("{{order_amount}}", formatCurrency(totalAmount, dto.getCurrencyCode()))
                .replace("{{order_items}}", items.toString().trim());
    }

    private String orderId(OrderEmailDto dto) {
        if (dto.getOriginOrderId() != null) {
            return dto.getOriginOrderId();
        }
        return dto.getId() == null ? "" : dto.getId().toString();
    }

    public record RenderedOrderEmail(String subject, String content) {
    }
}
