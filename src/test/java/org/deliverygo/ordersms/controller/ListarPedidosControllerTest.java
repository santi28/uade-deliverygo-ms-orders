package org.deliverygo.ordersms.controller;

import org.deliverygo.ordersms.dto.PaginaPedidosResponse;
import org.deliverygo.ordersms.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API-06 — contrato HTTP del listado paginado. */
@WebMvcTest(PedidoController.class)
class ListarPedidosControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PedidoService service;

    @Test
    void listaConValoresPorDefecto() throws Exception {
        when(service.listarPedidos(CLIENTE, 0, 20))
                .thenReturn(new PaginaPedidosResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/orders").header("X-Cliente-Id", CLIENTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pedidos").isEmpty())
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanio").value(20));
        verify(service).listarPedidos(CLIENTE, 0, 20);
    }
    @Test
    void paginaExplicitaNoAceptaClienteEnQueryComoSustituto() throws Exception {
        when(service.listarPedidos(CLIENTE, 2, 5))
                .thenReturn(new PaginaPedidosResponse(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/v1/orders").header("X-Cliente-Id", CLIENTE)
                .param("pagina", "2").param("tamanio", "5").param("clienteId", "otro"))
                .andExpect(status().isOk());
        verify(service).listarPedidos(CLIENTE, 2, 5);
    }
    @Test
    void cabeceraFaltanteOInvalidaNoConsulta() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders").header("X-Cliente-Id", "invalido"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test
    void paginaNoNumericaNoConsulta() throws Exception {
        mvc.perform(get("/api/v1/orders").header("X-Cliente-Id", CLIENTE).param("pagina", "x"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
