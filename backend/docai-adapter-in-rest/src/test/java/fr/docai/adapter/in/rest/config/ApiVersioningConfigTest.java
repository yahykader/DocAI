package fr.docai.adapter.in.rest.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Verifies /v1 prefix applied to all REST adapter controllers (C-04).
 * Uses a minimal MVC context to avoid Spring Boot security autoconfiguration.
 */
@SpringJUnitWebConfig(ApiVersioningConfigTest.MvcConfig.class)
class ApiVersioningConfigTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void v1PrefixApplied() throws Exception {
        mockMvc.perform(get("/v1/test-resource"))
            .andExpect(status().isOk());
    }

    @Test
    void withoutPrefixReturns404() throws Exception {
        mockMvc.perform(get("/test-resource"))
            .andExpect(status().isNotFound());
    }

    /** Minimal MVC context: versioning config + test controller, no security. */
    @Configuration
    @EnableWebMvc
    @Import({ApiVersioningConfig.class, ApiVersioningConfigTest.TestController.class})
    static class MvcConfig {}

    /** Minimal controller in the REST adapter package to verify /v1 routing. */
    @RestController
    @RequestMapping("/test-resource")
    static class TestController {

        /** Returns 200 OK for versioning path-prefix verification. */
        @GetMapping
        public String ping() {
            return "ok";
        }
    }
}
