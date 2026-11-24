package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.yaml.snakeyaml.Yaml;

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

    /**
     * Swagger UI fills a parameter's field on "Try it out" from the parameter's {@code examples}, {@code example},
     * {@code default} or {@code content} example, or its schema's {@code example} or {@code default}. A pre-filled key
     * would make every POST sent from Swagger UI after the first a replay that adds no interest.
     */
    @Test
    void swaggerUiLeavesTheIdempotencyKeyFieldEmpty() throws Exception {
        Map<String, Object> idempotencyKey = idempotencyKeyParameter(servedContract());

        assertThat(idempotencyKey).containsEntry("required", false);
        assertThat(idempotencyKey).doesNotContainKeys("example", "examples", "default", "content");
        assertThat(asMap(idempotencyKey.get("schema"))).doesNotContainKeys("example", "default");
    }

    private Map<String, Object> servedContract() throws Exception {
        MvcTestResult result = mvc.get().uri("/openapi.yaml").exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return new Yaml().load(result.getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> idempotencyKeyParameter(Map<String, Object> contract) {
        Map<String, Object> balanceUpdates = asMap(asMap(contract.get("paths")).get("/time-deposits/balance-updates"));
        List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) asMap(balanceUpdates.get("post")).get("parameters");
        return parameters.stream()
                .filter(parameter -> "Idempotency-Key".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }
}
