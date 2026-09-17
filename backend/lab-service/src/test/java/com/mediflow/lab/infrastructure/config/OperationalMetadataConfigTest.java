package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

class OperationalMetadataConfigTest {

    @Test
    void infoEndpoint_exposesConfiguredAppMetadata() throws Exception {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("lab-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        InfoContributorAutoConfiguration.class,
                        InfoEndpointAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(properties))
                .run(context -> {
                    InfoEndpoint endpoint = context.getBean(InfoEndpoint.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> app = (Map<String, Object>) endpoint.info().get("app");

                    assertThat(app)
                            .containsEntry("name", "MediFlow Lab Service")
                            .containsEntry("description", "Chỉ định xét nghiệm và kết quả xét nghiệm.")
                            .containsEntry("version", "v1");
                });
    }

    @Test
    void applicationConfiguration_exposesOnlyHealthAndInfoWithSafeAppMetadata() throws Exception {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("lab-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(properties);
        WebEndpointProperties endpointProperties = Binder.get(environment)
                .bind("management.endpoints.web", Bindable.of(WebEndpointProperties.class))
                .get();

        assertThat(endpointProperties.getExposure().getInclude())
                .containsExactlyInAnyOrder("health", "info");
        assertThat(properties.getProperty("management.info.env.enabled"))
                .isEqualTo(true);
        assertThat(properties.getProperty("info.app.name"))
                .isEqualTo("MediFlow Lab Service");
        assertThat(properties.getProperty("info.app.version"))
                .isEqualTo("v1");
        assertThat(properties.getProperty("info.app.description"))
                .asString()
                .isNotBlank();
        assertThat(properties.containsProperty("management.endpoints.web.exposure.include"))
                .isTrue();
    }
}
