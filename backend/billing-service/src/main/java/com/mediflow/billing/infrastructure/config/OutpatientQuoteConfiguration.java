package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.service.IssueOutpatientQuoteService;
import com.mediflow.billing.infrastructure.pricing.OutpatientQuoteCatalogAdapter;
import com.mediflow.billing.infrastructure.pricing.OutpatientQuoteProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
@EnableConfigurationProperties(OutpatientQuoteProperties.class)
public class OutpatientQuoteConfiguration {
    @Bean OutpatientQuoteCatalogPort outpatientQuoteCatalogue(PriceCatalogPort prices, OutpatientQuoteProperties policy) {
        return new OutpatientQuoteCatalogAdapter(prices, policy);
    }
    @Bean IssueOutpatientQuoteService issueOutpatientQuotes(OutpatientQuoteRepositoryPort quotes) {
        return new IssueOutpatientQuoteService(quotes);
    }
}
