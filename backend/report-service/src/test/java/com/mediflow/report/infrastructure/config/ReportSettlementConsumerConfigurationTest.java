package com.mediflow.report.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.port.in.ApplySettlementSnapshotUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.messaging.consumer.SettlementSnapshotConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReportSettlementConsumerConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ReportSettlementConsumerConfiguration.class)
                .withBean(CareFinanceWirePort.class,()->mock(CareFinanceWirePort.class))
                .withBean(ConnectionFactory.class,()->mock(ConnectionFactory.class))
                .withBean("reportJdbcTemplate", JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(ObjectMapper.class,ObjectMapper::new)
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void configuration_requiresBothGates(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2="+((flags&1)!=0),"mediflow.report.settlement-consumer.enabled="+((flags&2)!=0))
                .run(context->{ assertThat(context).hasNotFailed();
                    if(flags==3) { assertThat(context).hasSingleBean(ApplySettlementSnapshotUseCase.class); assertThat(context).hasSingleBean(SettlementSnapshotConsumer.class); }
                    else assertThat(context).doesNotHaveBean(SettlementSnapshotConsumer.class);
                });
    }
    @Test void configuration_noFlags_noQueueOrConsumer() { runner().run(context->assertThat(context).doesNotHaveBean("reportSettlementTopology")); }
    @Test void topology_onlyNonFinalReconciliationKeyWithDedicatedDurableDlq() {
        var topology=new ReportSettlementConsumerConfiguration().reportSettlementTopology();
        assertThat(topology.getDeclarablesByType(Binding.class).stream().filter(b->b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey))
                .containsExactly("settlement.reconciled");
        assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(2).allMatch(Queue::isDurable);
    }
}
