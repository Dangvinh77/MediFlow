package com.mediflow.gateway.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountSecurityProxyControllerTest {
    @Test
    void disabledMailboxReportsFalseAndBlocksEveryMailboxReadBeforeCoreCall() throws Exception {
        var core = mock(AccountSecurityProxyClient.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var controller = new AccountSecurityProxyController(core, mapper, false);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/demo/mailbox/config"));

        var configResponse = controller.mailboxConfig(exchange).block();
        ApiResponse<AccountSecurityProxyController.DemoMailboxConfigResponse> config = mapper.readValue(
                configResponse.getBody(), new TypeReference<>() { });
        assertThat(config.data().enabled()).isFalse();

        var listExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/demo/mailbox/accounts"));
        var list = controller.mailboxAccounts(null, null, 0, 20, listExchange).block();
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(core);
    }
}
