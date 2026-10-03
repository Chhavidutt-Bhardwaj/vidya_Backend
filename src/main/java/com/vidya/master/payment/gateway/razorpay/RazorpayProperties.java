package com.vidya.master.payment.gateway.razorpay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vidya.payment.razorpay")
public class RazorpayProperties {
    private String keyId = "rzp_test_vidya_key";
    private String keySecret = "rzp_test_vidya_secret";
    private String webhookSecret = "rzp_test_webhook_secret";
}
