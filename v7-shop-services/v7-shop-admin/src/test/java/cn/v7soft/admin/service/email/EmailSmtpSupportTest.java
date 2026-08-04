package cn.v7soft.admin.service.email;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSmtpSupportTest {

    private final EmailSmtpSupport support = new EmailSmtpSupport();

    @Test
    void signatureChangesWhenAnyCredentialChanges() {
        JSONObject original = smtp();
        JSONObject changed = new JSONObject(original);
        changed.set("password", "new-password");

        assertThat(support.signature(original)).isNotEqualTo(support.signature(changed));
    }

    @Test
    void rejectsIncompleteSmtpConfiguration() {
        JSONObject config = smtp();
        config.set("from", "");

        assertThatThrownBy(() -> support.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发件人");
    }

    @Test
    void defaultsToSmtpForExistingConfigurations() {
        assertThat(support.provider(smtp())).isEqualTo(EmailSmtpSupport.PROVIDER_SMTP);
    }

    @Test
    void validatesAmazonSesConfiguration() {
        JSONObject config = amazonSes();

        support.validate(config);

        assertThat(support.provider(config)).isEqualTo(EmailSmtpSupport.PROVIDER_AMAZON_SES);
    }

    @Test
    void amazonSesSignatureChangesWhenSecretChanges() {
        JSONObject original = amazonSes();
        JSONObject changed = new JSONObject(original).set("secret-access-key", "new-secret");

        assertThat(support.signature(original)).isNotEqualTo(support.signature(changed));
    }

    @Test
    void rejectsIncompleteAmazonSesConfiguration() {
        JSONObject config = amazonSes().set("region", "");

        assertThatThrownBy(() -> support.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区域");
    }

    @Test
    void fromAddressFallsBackToPlainAddressWithoutName() {
        assertThat(support.fromAddress(smtp()).toString()).isEqualTo("sender@example.com");
    }

    @Test
    void fromAddressRendersDisplayName() {
        JSONObject config = smtp().set("from-name", "Aoeermrs");

        assertThat(support.fromAddress(config).toString())
                .isEqualTo("Aoeermrs <sender@example.com>");
    }

    @Test
    void fromAddressEncodesNonAsciiDisplayName() {
        JSONObject config = smtp().set("from-name", "阿奥商城");

        String from = support.fromAddress(config).toString();

        assertThat(from).startsWith("=?UTF-8?").endsWith("<sender@example.com>");
    }

    @Test
    void fromAddressStripsLineBreaksFromDisplayName() {
        JSONObject config = smtp().set("from-name", "Aoeermrs\r\nBcc: attacker@example.com");

        String from = support.fromAddress(config).toString();

        assertThat(from).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void signatureIgnoresBlankFromName() {
        assertThat(support.signature(smtp().set("from-name", "")))
                .isEqualTo(support.signature(smtp()));
    }

    @Test
    void signatureChangesWhenFromNameChanges() {
        assertThat(support.signature(smtp().set("from-name", "Aoeermrs")))
                .isNotEqualTo(support.signature(smtp()));
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> support.validate(smtp().set("provider", "UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }

    private JSONObject smtp() {
        return new JSONObject()
                .set("host", "smtp.example.com")
                .set("port", 587)
                .set("username", "user")
                .set("password", "password")
                .set("from", "sender@example.com")
                .set("secure", false);
    }

    private JSONObject amazonSes() {
        return new JSONObject()
                .set("provider", "AMAZON_SES")
                .set("region", "eu-central-1")
                .set("access-key-id", "access-key")
                .set("secret-access-key", "secret-key")
                .set("from", "sender@example.com")
                .set("configuration-set", "orders");
    }
}
