package com.mediflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prevent reintroducing an unqualified transaction/JDBC/scheduler when composing the module. */
class ReportContextBindingTest {
    @Test void everyReportTransactionNamesItsOwnManagerAndEveryJdbcInjectionNamesItsOwnTemplate() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.mediflow.report");
        int transactions = 0, jdbcInjections = 0, schedules = 0;
        for (var imported : classes) {
            Class<?> type = imported.reflect();
            var classTx = type.getDeclaredAnnotation(Transactional.class);
            if (classTx != null) { assertManager(classTx); transactions++; }
            for (var method : type.getDeclaredMethods()) {
                var tx = method.getDeclaredAnnotation(Transactional.class);
                if (tx != null) { assertManager(tx, type.getName() + "#" + method.getName()); transactions++; }
                var scheduled = method.getDeclaredAnnotation(Scheduled.class);
                if (scheduled != null) {
                    assertThat(scheduled.scheduler()).isEqualTo("reportTaskScheduler"); schedules++;
                }
            }
            if (!AnnotatedElementUtils.hasAnnotation(type, Component.class)) continue;
            var constructors = type.getConstructors();
            for (var constructor : constructors) {
                if (constructors.length != 1 && !constructor.isAnnotationPresent(Autowired.class)) continue;
                for (var parameter : constructor.getParameters()) {
                    if (parameter.getType() != JdbcTemplate.class) continue;
                    assertThat(parameter.getAnnotation(Qualifier.class)).as(type.getName()).isNotNull();
                    assertThat(parameter.getAnnotation(Qualifier.class).value()).isEqualTo("reportJdbcTemplate");
                    jdbcInjections++;
                }
            }
        }
        assertThat(transactions).isGreaterThan(50);
        assertThat(jdbcInjections).isGreaterThan(10);
        assertThat(schedules).isEqualTo(4);
    }
    private void assertManager(Transactional tx) {
        assertManager(tx, "Report transaction");
    }
    private void assertManager(Transactional tx, String location) {
        assertThat(Arrays.asList(tx.value(), tx.transactionManager())).as(location).contains("reportTransactionManager");
    }
}
