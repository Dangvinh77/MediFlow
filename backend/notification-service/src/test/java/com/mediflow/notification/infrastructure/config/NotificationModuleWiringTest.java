package com.mediflow.notification.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;

class NotificationModuleWiringTest {
    private final NotificationModulePersistenceConfiguration config = new NotificationModulePersistenceConfiguration();

    @Test void storeHasItsOwnPoolAndNeverFallsBackToDefaultCredentials() {
        var missing = new DataSourceProperties();
        assertThatThrownBy(() -> config.notificationDataSource(missing, 4)).isInstanceOf(IllegalStateException.class);
        var properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://127.0.0.1:1/notification_test");
        properties.setUsername("notification_test"); properties.setPassword("notification_test");
        try (var source = config.notificationDataSource(properties, 3)) {
            assertThat(source.getMaximumPoolSize()).isEqualTo(3);
            assertThat(source.getPoolName()).isEqualTo("notification-store");
            assertThat(source.getConnectionTimeout()).isEqualTo(3000);
        }
        assertThatThrownBy(() -> config.notificationDataSource(properties, 17)).isInstanceOf(IllegalStateException.class);
    }

    @Test void flywayAndRepositoriesUseOnlyNotificationStoreAndUnchangedMigrations() {
        var source = mock(DataSource.class);
        var flyway = config.notificationFlyway(source);
        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(source);
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(flyway.getConfiguration().getLocations()).extracting(Object::toString)
                .containsExactly("classpath:mediflow/notification/db/migration");
        var repositories = NotificationModulePersistenceConfiguration.class.getAnnotation(EnableJpaRepositories.class);
        assertThat(repositories.entityManagerFactoryRef()).isEqualTo("notificationEntityManagerFactory");
        assertThat(repositories.transactionManagerRef()).isEqualTo("notificationTransactionManager");
        assertThat(config.notificationEntityManagerFactory(source).getPersistenceUnitName()).isEqualTo("notification");
        assertThat(config.notificationJdbcTemplate(source).getDataSource()).isSameAs(source);
    }

    @Test void moduleRequiresOutpatientProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("notification-module");
        var guard = NotificationModuleConfiguration.notificationModuleModeGuard(environment);
        assertThatThrownBy(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class);
        environment.setActiveProfiles("notification-module", "outpatient-mvp");
        assertThatCode(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory())).doesNotThrowAnyException();
    }
}
