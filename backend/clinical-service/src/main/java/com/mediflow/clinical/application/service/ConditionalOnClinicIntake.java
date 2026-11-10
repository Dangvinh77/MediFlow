package com.mediflow.clinical.application.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/** Beans that exist only when scheduling v2 and the clinic intake flag are both on. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnExpression("${mediflow.clinical.scheduling.enabled:false} and ${mediflow.v2.clinic-intake:false}")
public @interface ConditionalOnClinicIntake {
}
