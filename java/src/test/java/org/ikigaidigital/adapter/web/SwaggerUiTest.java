package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SwaggerUiTest extends PostgresIntegrationTest {

    @Test
    void contractIsServedAsWritten() {
        assertThat(mvc.get().uri("/openapi.yaml"))
                .hasStatus(HttpStatus.OK)
                .body()
                .asString()
                .contains("/time-deposits:", "operationId: getTimeDeposits");
    }

    @Test
    void swaggerUiIsServedAndShowsTheContract() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/v3/api-docs/swagger-config"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.url")
                .isEqualTo("/openapi.yaml");
    }
}
