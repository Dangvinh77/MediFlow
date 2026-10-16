package com.mediflow.packaging;

import static org.mockito.Mockito.mock;
import com.mediflow.report.infrastructure.config.ReportModuleConfiguration;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

/** Isolated harness outside Report's scan; deliberately not a standalone Report boot launcher. */
@Configuration(proxyBeanMethods=false)
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,JdbcTemplateAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,FlywayAutoConfiguration.class})
@Import(ReportModuleConfiguration.class)
public class ReportDatabaseTestHost {
    @Bean ConnectionFactory connectionFactory() { return mock(ConnectionFactory.class); }
    @Bean DataSource otherDataSource(@Value("${test.other.datasource.url}") String url,
            @Value("${test.other.datasource.username}") String username,
            @Value("${test.other.datasource.password}") String password) {
        return new DriverManagerDataSource(url,username,password);
    }
    @Bean JdbcTemplate otherJdbcTemplate(@Qualifier("otherDataSource") DataSource ds) { return new JdbcTemplate(ds); }
    @Bean PlatformTransactionManager otherTransactionManager(@Qualifier("otherDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
