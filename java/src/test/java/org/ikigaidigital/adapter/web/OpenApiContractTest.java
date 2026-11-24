package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.yaml.snakeyaml.Yaml;

/** The contract as served at {@code /openapi.yaml}, which Swagger UI shows. */
class OpenApiContractTest extends ApiIntegrationTest {

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
        Map<String, Object> balanceUpdates =
                asMap(asMap(contract.get("paths")).get("/v1/time-deposits/balance-updates"));
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
