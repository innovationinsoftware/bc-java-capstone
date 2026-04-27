package com.example.banking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test: /health is reachable without a token. Used by the SPA before
 * showing the login button.
 *
 * This is the ONLY public endpoint in the API. If you add another, add a
 * test for it here so it's deliberate.
 */
@WebMvcTest(controllers = HealthController.class)
@Import({com.example.banking.config.SecurityConfig.class,
         com.example.banking.config.CorsConfig.class})
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void health_is_public() throws Exception {
        mvc.perform(get("/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
}
