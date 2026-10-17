package com.mediflow.notification.infrastructure.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Names the existing standalone store; never imported by a co-host. */
@Configuration(proxyBeanMethods = false)
@Profile("!notification-module")
public class NotificationStandalonePersistenceConfiguration {
    @Bean(name = {"notificationJdbcTemplate", "jdbcTemplate"})
    JdbcTemplate notificationJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = {"notificationTransactionManager", "transactionManager"})
    PlatformTransactionManager notificationTransactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
