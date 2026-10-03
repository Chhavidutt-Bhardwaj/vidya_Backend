package com.vidya.master.payment.gateway;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.payment.entity.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that resolves the appropriate {@link PaymentGatewayProvider} based on the {@link PaymentProvider} type.
 */
@Slf4j
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentProvider, PaymentGatewayProvider> gatewayMap = new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayRegistry(List<PaymentGatewayProvider> providers) {
        for (PaymentGatewayProvider provider : providers) {
            gatewayMap.put(provider.getProviderType(), provider);
            log.info("Registered payment gateway provider: {}", provider.getProviderType());
        }
    }

    public PaymentGatewayProvider getGateway(PaymentProvider provider) {
        if (provider == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Payment provider must not be null");
        }
        PaymentGatewayProvider gateway = gatewayMap.get(provider);
        if (gateway == null) {
            throw new ResourceNotFoundException("PaymentGatewayProvider", provider.name());
        }
        return gateway;
    }
}
