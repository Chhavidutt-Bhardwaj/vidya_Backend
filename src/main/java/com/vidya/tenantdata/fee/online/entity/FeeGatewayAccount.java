package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.GatewayEnvironment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** The school's own merchant account at one gateway. Secrets are stored encrypted ({@code SecretCipher}) and never returned. */
@Entity
@Table(name = "fee_gateway_account")
@Getter
@Setter
@NoArgsConstructor
public class FeeGatewayAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private GatewayProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatewayEnvironment environment = GatewayEnvironment.SANDBOX;

    @Column(name = "key_id", nullable = false, length = 200)
    private String keyId;

    @Column(name = "secret_enc", nullable = false)
    private String secretEnc;

    @Column(name = "webhook_secret_enc")
    private String webhookSecretEnc;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
