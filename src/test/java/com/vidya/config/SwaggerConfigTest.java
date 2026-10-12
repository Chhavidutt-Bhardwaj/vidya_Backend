package com.vidya.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Swagger & SpringDoc OpenAPI — Profile Configuration Tests")
class SwaggerConfigTest {

    @Test
    @DisplayName("Production profile explicitly disables Swagger UI and OpenAPI /v3/api-docs")
    void prodProfile_disablesSwaggerAndApiDocs() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-prod.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);

            @SuppressWarnings("unchecked")
            Map<String, Object> springdoc = (Map<String, Object>) data.get("springdoc");
            assertThat(springdoc).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> apiDocs = (Map<String, Object>) springdoc.get("api-docs");
            assertThat(apiDocs).isNotNull();
            assertThat(apiDocs.get("enabled")).isEqualTo(false);

            @SuppressWarnings("unchecked")
            Map<String, Object> swaggerUi = (Map<String, Object>) springdoc.get("swagger-ui");
            assertThat(swaggerUi).isNotNull();
            assertThat(swaggerUi.get("enabled")).isEqualTo(false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Base / dev profile keeps Swagger UI and /v3/api-docs configured and accessible")
    void baseProfile_enablesSwaggerAndApiDocs() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).isNotNull();
            Map<String, Object> data = yaml.load(in);

            @SuppressWarnings("unchecked")
            Map<String, Object> springdoc = (Map<String, Object>) data.get("springdoc");
            assertThat(springdoc).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> apiDocs = (Map<String, Object>) springdoc.get("api-docs");
            assertThat(apiDocs).isNotNull();
            assertThat(apiDocs.get("path")).isEqualTo("/v3/api-docs");

            @SuppressWarnings("unchecked")
            Map<String, Object> swaggerUi = (Map<String, Object>) springdoc.get("swagger-ui");
            assertThat(swaggerUi).isNotNull();
            assertThat(swaggerUi.get("path")).isEqualTo("/swagger-ui.html");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
