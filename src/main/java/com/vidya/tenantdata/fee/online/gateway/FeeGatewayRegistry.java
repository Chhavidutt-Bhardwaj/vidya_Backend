package com.vidya.tenantdata.fee.online.gateway;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The gateway implementation per provider. */
@Component
public class FeeGatewayRegistry {

    private final Map<GatewayProvider, FeeGateway> byProvider = new EnumMap<>(GatewayProvider.class);

    public FeeGatewayRegistry(List<FeeGateway> gateways) {
        gateways.forEach(g -> byProvider.put(g.provider(), g));
    }

    public FeeGateway get(GatewayProvider provider) {
        FeeGateway g = byProvider.get(provider);
        if (g == null) {
            throw new IllegalStateException("No gateway for " + provider);
        }
        return g;
    }
}
