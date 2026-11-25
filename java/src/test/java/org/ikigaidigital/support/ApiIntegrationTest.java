package org.ikigaidigital.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base class for API tests: HTTP requests through MockMvc against the whole application. {@link #client} sends its
 * requests through the same MockMvc and reads response bodies into the API model classes generated from the contract;
 * {@link #mvc} asserts on the raw response. Test data is inserted with the {@link DatabaseIntegrationTest} helpers;
 * results are read back through the API.
 */
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
public abstract class ApiIntegrationTest extends DatabaseIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected RestTestClient client;
}
