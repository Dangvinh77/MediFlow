package com.mediflow.report.support;

import com.mediflow.report.infrastructure.config.ReportStandalonePersistenceConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** A JPA slice still uses the same explicitly named store as the standalone production app. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ReportStandalonePersistenceConfiguration.class)
public @interface ReportPersistenceTest { }
