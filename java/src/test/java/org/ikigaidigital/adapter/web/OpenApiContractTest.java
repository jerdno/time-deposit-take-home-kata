package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yaml.snakeyaml.Yaml;

/** The contract as served at {@code /openapi.yaml}, which Swagger UI shows. */
class OpenApiContractTest extends ApiIntegrationTest {

    /**
     * Swagger UI fills a parameter's field on "Try it out" from the parameter's {@code examples}, {@code example} or
     * {@code default}, or its schema's {@code example} or {@code default}. A pre-filled key would make every POST sent
     * from Swagger UI after the first a replay that adds no interest.
     */
    @Test
    void swaggerUiLeavesTheIdempotencyKeyFieldEmpty() throws Exception {
        Map<String, Object> idempotencyKey = balanceUpdateParameter(servedContract(), "Idempotency-Key");

        assertThat(idempotencyKey).containsEntry("required", false);
        assertThat(idempotencyKey).doesNotContainKeys("example", "examples", "default", "content");
        assertThat(asMap(idempotencyKey.get("schema"))).doesNotContainKeys("example", "default");
    }

    private Map<String, Object> servedContract() throws Exception {
        MockHttpServletResponse response =
                mvc.get().uri("/openapi.yaml").exchange().getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        return new Yaml().load(response.getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> balanceUpdateParameter(Map<String, Object> contract, String name) {
        Map<String, Object> balanceUpdates = asMap(asMap(contract.get("paths")).get("/time-deposits/balance-updates"));
        List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) asMap(balanceUpdates.get("post")).get("parameters");
        return parameters.stream()
                .filter(parameter -> name.equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        return (Map<String, Object>) node;
    }
}
