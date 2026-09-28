package com.mediflow.surgery.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Keeps Surgery business routes and behavior disabled until their integration gates pass. */
@ConfigurationProperties(prefix = "mediflow.features.surgery")
public record SurgeryFeatureProperties(boolean enabled) {
}
