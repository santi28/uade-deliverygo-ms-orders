package org.deliverygo.ordersms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regresiones de creacion/reutilizacion de borradores y validacion de lineas nulas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoControllerRegressionTest {

    private static final String PEDIDOS = "/api/v1/orders";
    private static final String CLIENTE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PIZZA_ID = "33333333-3333-3333-3333-333333333333";
    private static final String EMPANADA_ID = "44444444-4444-4444-4444-444444444444";
    private static final String CREAR_PEDIDO = """
            {
              "comercioId": "99999999-9999-9999-9999-999999999999",
              "direccionEntregaId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private HistorialEstadoRepository historialEstadoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void crearPedidoNuevoResponde201ConLocation() throws Exception {
        long pedidosAntes = pedidoRepository.count();
        long historialAntes = historialEstadoRepository.count();

        MvcResult resultado = solicitarCreacion()
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        JsonNode pedido = cuerpo(resultado);
        UUID pedidoId = idDe(pedido);
        assertThat(URI.create(resultado.getResponse().getHeader(HttpHeaders.LOCATION)).getPath())
                .isEqualTo(PEDIDOS + "/" + pedidoId);
        assertThat(pedido.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(pedido.path("total").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pedido.path("items").size()).isZero();
        assertThat(pedido.path("historial").size()).isEqualTo(1);
        assertThat(pedidoRepository.count()).isEqualTo(pedidosAntes + 1);
        assertThat(historialEstadoRepository.count()).isEqualTo(historialAntes + 1);
    }

    @Test
    void repetirCreacionDevuelve200SinDuplicarPedidoNiHistorial() throws Exception {
        long pedidosAntes = pedidoRepository.count();
        long historialAntes = historialEstadoRepository.count();
        MvcResult creacion = solicitarCreacion().andExpect(status().isCreated()).andReturn();
        JsonNode creado = cuerpo(creacion);

        JsonNode reutilizado = cuerpo(solicitarCreacion()
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        creacion.getResponse().getHeader(HttpHeaders.LOCATION)))
                .andReturn());

        assertThat(idDe(reutilizado)).isEqualTo(idDe(creado));
        assertThat(reutilizado).isEqualTo(creado);
        assertThat(reutilizado.path("historial")).isEqualTo(creado.path("historial"));
        assertThat(pedidoRepository.count()).isEqualTo(pedidosAntes + 1);
        assertThat(historialEstadoRepository.count()).isEqualTo(historialAntes + 1);
    }

    @Test
    void rechazaElementoNuloCon400YConservaElPedido() throws Exception {
        JsonNode antes = crearBorradorConEmpanada();
        UUID pedidoId = idDe(antes);

        rechazarItems(pedidoId, """
                {"items": [null]}
                """);

        assertThat(consultarPersistido(pedidoId)).isEqualTo(antes);
    }

    @Test
    void rechazaLoteConLineaValidaYNulaSinAgregarParcialmente() throws Exception {
        JsonNode antes = crearBorradorConEmpanada();
        UUID pedidoId = idDe(antes);

        rechazarItems(pedidoId, """
                {
                  "items": [
                    {"productoId": "33333333-3333-3333-3333-333333333333", "cantidad": 2},
                    null
                  ]
                }
                """);

        assertThat(consultarPersistido(pedidoId)).isEqualTo(antes);
    }

    @Test
    void conservaLaCargaDeLineasValidasCon200() throws Exception {
        UUID pedidoId = idDe(crearBorrador());

        JsonNode actualizado = agregarItems(pedidoId, """
                {
                  "items": [
                    {"productoId": "33333333-3333-3333-3333-333333333333", "cantidad": 2},
                    {"productoId": "44444444-4444-4444-4444-444444444444", "cantidad": 3}
                  ]
                }
                """);

        assertThat(actualizado.path("estado").asText()).isEqualTo("BORRADOR");
        assertThat(actualizado.path("items").size()).isEqualTo(2);
        assertThat(itemDe(actualizado, PIZZA_ID).path("cantidad").asInt()).isEqualTo(2);
        assertThat(itemDe(actualizado, EMPANADA_ID).path("cantidad").asInt()).isEqualTo(3);
        assertThat(actualizado.path("total").decimalValue())
                .isEqualByComparingTo(new BigDecimal("20600.00"));
        assertThat(actualizado.path("historial").size()).isEqualTo(2);
    }

    @Test
    void conservaElRechazoDeListaVaciaCon400() throws Exception {
        UUID pedidoId = idDe(crearBorrador());
        JsonNode antes = consultarPersistido(pedidoId);

        rechazarItems(pedidoId, """
                {"items": []}
                """);

        assertThat(consultarPersistido(pedidoId)).isEqualTo(antes);
    }

    private ResultActions solicitarCreacion() throws Exception {
        return mvc.perform(post(PEDIDOS)
                .header(PedidoController.HEADER_CLIENTE, CLIENTE_ID)
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .content(CREAR_PEDIDO));
    }

    private JsonNode crearBorrador() throws Exception {
        return cuerpo(solicitarCreacion()
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
    }

    private JsonNode crearBorradorConEmpanada() throws Exception {
        UUID pedidoId = idDe(crearBorrador());
        agregarItems(pedidoId, """
                {"items": [{"productoId": "44444444-4444-4444-4444-444444444444", "cantidad": 1}]}
                """);
        return consultarPersistido(pedidoId);
    }

    private JsonNode agregarItems(UUID pedidoId, String contenido) throws Exception {
        return cuerpo(mvc.perform(post(PEDIDOS + "/{id}/items", pedidoId)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE_ID)
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(contenido))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
    }

    private void rechazarItems(UUID pedidoId, String contenido) throws Exception {
        mvc.perform(post(PEDIDOS + "/{id}/items", pedidoId)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE_ID)
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private JsonNode consultarPersistido(UUID pedidoId) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return cuerpo(mvc.perform(get(PEDIDOS + "/{id}", pedidoId)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE_ID)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn());
    }

    private JsonNode cuerpo(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsByteArray());
    }

    private UUID idDe(JsonNode pedido) {
        return UUID.fromString(pedido.path("id").asText());
    }

    private JsonNode itemDe(JsonNode pedido, String productoId) {
        for (JsonNode item : pedido.path("items")) {
            if (productoId.equals(item.path("productoId").asText())) {
                return item;
            }
        }
        throw new AssertionError("No aparece el producto " + productoId);
    }
}
