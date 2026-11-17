package org.deliverygo.ordersms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health check: GET /status responde 200 con el texto "OK", sin depender de nada. */
@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void devuelve200ConElTextoOk() throws Exception {
        mvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("OK"));
    }
}
