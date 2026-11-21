package org.deliverygo.ordersms.controller;

import org.deliverygo.ordersms.dto.ConfirmarPedidoRequest;
import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.ModificarCantidadItemRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.dto.ResultadoPagoRequest;
import org.deliverygo.ordersms.dto.RevalidacionPreciosResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.GlobalExceptionHandler;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.deliverygo.ordersms.mapper.PedidoMapper;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.service.PedidoService;
import org.deliverygo.ordersms.service.PedidoService.ResultadoCreacionPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.DIRECCION;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-01 a API-05 — Contrato HTTP del controller y del {@link GlobalExceptionHandler}.
 *
 * <p>Verifica lo que es responsabilidad de la capa web: el status y el cuerpo
 * {@code ErrorResponse} de cada codigo de la tabla de errores (400, 404, 409, 422, 503),
 * la validacion de DTOs con Bean Validation y que cada endpoint le pase al service la
 * identidad de {@code X-Cliente-Id} y los ids de la ruta.
 *
 * <p>El service va mockeado a proposito: las reglas de negocio se prueban en los tests de
 * Service, y el camino completo contra la base en {@code PedidoControllerRegressionTest}.
 */
@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    private static final String PEDIDOS = "/api/v1/orders";
    private static final String CREAR_PEDIDO = """
            {
              "comercioId": "%s",
              "direccionEntregaId": "%s"
            }
            """.formatted(COMERCIO, DIRECCION);
    private static final String AGREGAR_ITEM = """
            {
              "items": [ { "productoId": "%s", "cantidad": 2 } ]
            }
            """.formatted(PRODUCTO);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    private static PedidoResponse respuestaDe(Pedido pedido) {
        return new PedidoMapper().aResponse(pedido, pedido.getHistorial());
    }

    // ------------------------------------------------------- forma de las respuestas

    @Test
    @DisplayName("API-01: crear responde 201 con Location del pedido")
    void crearResponde201ConLocation() throws Exception {
        Pedido pedido = borradorConItem(1);
        given(pedidoService.crearPedido(eq(CLIENTE), any(CrearPedidoRequest.class)))
                .willReturn(new ResultadoCreacionPedido(respuestaDe(pedido), true));

        mvc.perform(post(PEDIDOS)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content(CREAR_PEDIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(PEDIDOS + "/" + PEDIDO_ID)))
                .andExpect(jsonPath("$.id").value(PEDIDO_ID.toString()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("API-01: reutilizar el borrador abierto responde 200, no 201")
    void reutilizarBorradorResponde200() throws Exception {
        given(pedidoService.crearPedido(eq(CLIENTE), any(CrearPedidoRequest.class)))
                .willReturn(new ResultadoCreacionPedido(respuestaDe(borradorConItem(1)), false));

        mvc.perform(post(PEDIDOS)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content(CREAR_PEDIDO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith(PEDIDOS + "/" + PEDIDO_ID)));
    }

    @Test
    @DisplayName("API-02: agregar lineas responde 200 con el pedido recalculado")
    void agregarItemsResponde200() throws Exception {
        given(pedidoService.agregarItems(eq(PEDIDO_ID), eq(CLIENTE), any()))
                .willReturn(respuestaDe(borradorConItem(2)));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/items")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content(AGREGAR_ITEM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productoId").value(PRODUCTO.toString()))
                .andExpect(jsonPath("$.total").value(17000.00));
    }

    @Test
    @DisplayName("API-02b: cambiar la cantidad responde 200 y le pasa al service el producto de la ruta")
    void modificarCantidadResponde200() throws Exception {
        given(pedidoService.modificarCantidadItem(eq(PEDIDO_ID), eq(CLIENTE), eq(PRODUCTO), any()))
                .willReturn(respuestaDe(borradorConItem(5)));

        mvc.perform(put(PEDIDOS + "/" + PEDIDO_ID + "/items/" + PRODUCTO)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cantidad\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(5));

        verify(pedidoService).modificarCantidadItem(eq(PEDIDO_ID), eq(CLIENTE), eq(PRODUCTO),
                eq(new ModificarCantidadItemRequest(5)));
    }

    @Test
    @DisplayName("API-02c: quitar la linea responde 200 con el pedido, no 204")
    void quitarItemResponde200() throws Exception {
        given(pedidoService.quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO))
                .willReturn(respuestaDe(borradorConItem(1)));

        mvc.perform(delete(PEDIDOS + "/" + PEDIDO_ID + "/items/" + PRODUCTO)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists());

        verify(pedidoService).quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO);
    }

    @Test
    @DisplayName("API-03: confirmar responde 200 con el pedido CONFIRMADO y su pagoId")
    void confirmarResponde200() throws Exception {
        given(pedidoService.confirmarPedido(eq(PEDIDO_ID), eq(CLIENTE), any(ConfirmarPedidoRequest.class)))
                .willReturn(respuestaDe(enEstado(EstadoPedido.CONFIRMADO, PAGO_ID)));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/confirm")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"medioPago\": \"TARJETA_CREDITO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.pagoId").value(PAGO_ID.toString()));

        verify(pedidoService).confirmarPedido(PEDIDO_ID, CLIENTE,
                new ConfirmarPedidoRequest("TARJETA_CREDITO"));
    }

    @Test
    @DisplayName("API-04: cancelar responde 200 con el pedido CANCELADO")
    void cancelarResponde200() throws Exception {
        given(pedidoService.cancelarPedido(PEDIDO_ID, CLIENTE))
                .willReturn(respuestaDe(enEstado(EstadoPedido.CANCELADO, null)));

        mvc.perform(delete(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        verify(pedidoService).cancelarPedido(PEDIDO_ID, CLIENTE);
    }

    @Test
    @DisplayName("API-05: consultar responde 200 con items e historial")
    void consultarResponde200() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willReturn(respuestaDe(enEstado(EstadoPedido.CONFIRMADO, PAGO_ID)));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("BORRADOR"))
                .andExpect(jsonPath("$.historial[1].estadoNuevo").value("CONFIRMADO"));

        verify(pedidoService).consultarPedido(PEDIDO_ID, CLIENTE);
    }

    @Test
    @DisplayName("API-07: revalidar precios responde 200 con las diferencias")
    void revalidarPreciosResponde200() throws Exception {
        given(pedidoService.revalidarPrecios(PEDIDO_ID, CLIENTE)).willReturn(new RevalidacionPreciosResponse(
                PEDIDO_ID, false, new BigDecimal("8500.00"), new BigDecimal("9200.00"), "ARS",
                List.of(new RevalidacionPreciosResponse.DiferenciaLinea(PRODUCTO, new BigDecimal("8500.00"),
                        new BigDecimal("9200.00"), true, "PRICE_CHANGED"))));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID + "/price-check")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.diferencias[0].motivo").value("PRICE_CHANGED"));

        verify(pedidoService).revalidarPrecios(PEDIDO_ID, CLIENTE);
    }

    @Test
    @DisplayName("API-09: el resultado del pago responde 200 y no pide la identidad del cliente")
    void resultadoPagoResponde200SinCliente() throws Exception {
        given(pedidoService.procesarResultadoPago(PEDIDO_ID, new ResultadoPagoRequest(PAGO_ID)))
                .willReturn(respuestaDe(enEstado(EstadoPedido.CONFIRMADO, PAGO_ID)));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/payment-result")
                        .contentType(APPLICATION_JSON)
                        .content("{\"codigoPago\": \"" + PAGO_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));

        verify(pedidoService).procesarResultadoPago(PEDIDO_ID, new ResultadoPagoRequest(PAGO_ID));
    }

    @Test
    @DisplayName("400, DTO-09: el codigo de pago es obligatorio")
    void rechazaResultadoPagoSinCodigo() throws Exception {
        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/payment-result")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("422: PAYMENT_MISMATCH sale con su codigo")
    void traducePagoQueNoCorrespondeA422() throws Exception {
        given(pedidoService.procesarResultadoPago(eq(PEDIDO_ID), any(ResultadoPagoRequest.class)))
                .willThrow(new ReglaNegocioException(CodigoError.PAYMENT_MISMATCH, "no corresponde"));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/payment-result")
                        .contentType(APPLICATION_JSON)
                        .content("{\"codigoPago\": \"" + PAGO_ID + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_MISMATCH"));
    }

    // ------------------------------------------------------------ 400 VALIDATION_ERROR

    @Test
    @DisplayName("400: falta la cabecera de identidad del cliente")
    void rechazaCabeceraFaltante() throws Exception {
        mvc.perform(post(PEDIDOS)
                        .contentType(APPLICATION_JSON)
                        .content(CREAR_PEDIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Falta la cabecera " + PedidoController.HEADER_CLIENTE));
    }

    @Test
    @DisplayName("400: la cabecera de cliente no es un UUID")
    void rechazaClienteMalFormado() throws Exception {
        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("400: el id del pedido no es un UUID")
    void rechazaPedidoIdMalFormado() throws Exception {
        mvc.perform(get(PEDIDOS + "/no-es-un-uuid")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Valor invalido para id"));
    }

    @Test
    @DisplayName("400, DTO-01: falta un campo obligatorio del cuerpo")
    void rechazaCuerpoIncompleto() throws Exception {
        mvc.perform(post(PEDIDOS)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"direccionEntregaId\": \"" + DIRECCION + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                // El mensaje nombra el campo que falta, para que el cliente sepa que corregir.
                .andExpect(jsonPath("$.message").value("comercioId: comercioId es obligatorio"));
    }

    @Test
    @DisplayName("400, DTO-03: la lista de lineas no puede venir vacia")
    void rechazaListaDeItemsVacia() throws Exception {
        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/items")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("400, DTO-05: la cantidad tiene que ser mayor a cero")
    void rechazaCantidadCero() throws Exception {
        // La cantidad 0 ya no quita la linea: para eso esta el DELETE (desvio del 27/10).
        mvc.perform(put(PEDIDOS + "/" + PEDIDO_ID + "/items/" + PRODUCTO)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cantidad\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("cantidad: cantidad tiene que ser mayor a cero"));
    }

    @Test
    @DisplayName("400, DTO-04: el medio de pago es obligatorio")
    void rechazaMedioDePagoVacio() throws Exception {
        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/confirm")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"medioPago\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("400: el cuerpo no es un JSON valido")
    void rechazaJsonIlegible() throws Exception {
        mvc.perform(post(PEDIDOS)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la peticion no es un JSON valido"));
    }

    @Test
    @DisplayName("400: una regla de negocio con VALIDATION_ERROR sale como 400")
    void traduceReglaDeNegocioA400() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new ReglaNegocioException(CodigoError.VALIDATION_ERROR, "El cliente no esta activo"));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El cliente no esta activo"));
    }

    // -------------------------------------------------------------------- 404, 409, 422

    @Test
    @DisplayName("404: un pedido inexistente o de otro cliente sale como ORDER_NOT_FOUND")
    void traducePedidoInexistenteA404() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new PedidoNotFoundException(PEDIDO_ID));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("409, RN-04: una transicion no permitida sale como ORDER_INVALID_STATE")
    void traduceEstadoInvalidoA409() throws Exception {
        given(pedidoService.cancelarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new EstadoInvalidoException(EstadoPedido.CANCELADO, EstadoPedido.CANCELADO));

        mvc.perform(delete(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                // Pendiente de confirmar con el docente: la consigna usa 409, su ejemplo 422.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @ParameterizedTest
    @CsvSource({
            // RN-02: producto de otro comercio.
            "MIXED_MERCHANT",
            // RN-05: producto inactivo o sin stock.
            "PRODUCT_UNAVAILABLE",
            // RN-03: el precio cambio antes de confirmar.
            "PRICE_CHANGED",
            // INT-03: Pagos rechazo el cobro.
            "PAYMENT_REJECTED"
    })
    @DisplayName("422: las reglas de negocio no procesables salen con su codigo")
    void traduceReglasDeNegocioA422(CodigoError codigo) throws Exception {
        given(pedidoService.confirmarPedido(eq(PEDIDO_ID), eq(CLIENTE), any(ConfirmarPedidoRequest.class)))
                .willThrow(new ReglaNegocioException(codigo, "Corta " + codigo));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/confirm")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"medioPago\": \"TARJETA_CREDITO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(codigo.name()))
                .andExpect(jsonPath("$.status").value(422));
    }

    // ------------------------------------------------- 503 y red de contencion del 500

    @Test
    @DisplayName("503, INT-01..04: una dependencia caida sale como DEPENDENCY_UNAVAILABLE")
    void traduceDependenciaCaidaA503() throws Exception {
        given(pedidoService.confirmarPedido(eq(PEDIDO_ID), eq(CLIENTE), any(ConfirmarPedidoRequest.class)))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RuntimeException("timeout")));

        mvc.perform(post(PEDIDOS + "/" + PEDIDO_ID + "/confirm")
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{\"medioPago\": \"TARJETA_CREDITO\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                // No se inventa un resultado: el cliente sabe que no se pudo cobrar.
                .andExpect(jsonPath("$.message").value("El modulo Pagos no esta disponible"));
    }

    @Test
    @DisplayName("500: un fallo no contemplado sale normalizado y sin stacktrace")
    void traduceFalloInesperadoA500() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new IllegalStateException("se rompio algo que no esta en la tabla"));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno del modulo de pedidos"));
    }

    @Test
    @DisplayName("405: un verbo no soportado respeta el status de Spring y no da 500")
    void traduceVerboNoSoportado() throws Exception {
        mvc.perform(put(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                // No es un error de negocio: no lleva un codigo de la tabla del plan.
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    // ------------------------------------------------------- formato de ErrorResponse

    @Test
    @DisplayName("ErrorResponse trae los seis campos del formato normalizado")
    void errorResponseTraeElFormatoNormalizado() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new PedidoNotFoundException(PEDIDO_ID));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value(PEDIDOS + "/" + PEDIDO_ID))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("ErrorResponse devuelve el X-Correlation-Id que llego, para poder rastrear el caso")
    void errorResponseRespetaElCorrelationIdEntrante() throws Exception {
        String correlacion = UUID.randomUUID().toString();
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new PedidoNotFoundException(PEDIDO_ID));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE)
                        .header(GlobalExceptionHandler.HEADER_CORRELACION, correlacion))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value(correlacion));
    }

    @Test
    @DisplayName("ErrorResponse genera un correlationId cuando la peticion no trae ninguno")
    void errorResponseGeneraCorrelationIdSiNoLlega() throws Exception {
        given(pedidoService.consultarPedido(PEDIDO_ID, CLIENTE))
                .willThrow(new PedidoNotFoundException(PEDIDO_ID));

        mvc.perform(get(PEDIDOS + "/" + PEDIDO_ID)
                        .header(PedidoController.HEADER_CLIENTE, CLIENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
