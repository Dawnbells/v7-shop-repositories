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

    private JSONObject smtp() {
        return new JSONObject()
                .set("host", "smtp.example.com")
                .set("port", 587)
                .set("username", "user")
                .set("password", "password")
                .set("from", "sender@example.com")
                .set("secure", false);
    }
}
