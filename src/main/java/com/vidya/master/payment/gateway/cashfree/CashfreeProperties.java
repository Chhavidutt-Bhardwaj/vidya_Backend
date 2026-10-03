package com.vidya.master.payment.gateway.cashfree;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vidya.payment.cashfree")
public class CashfreeProperties {
    private String appId = "cf_test_vidya_app";
    private String secretKey = "cf_test_vidya_secret";
}
