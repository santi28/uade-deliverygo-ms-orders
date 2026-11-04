package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * INT-03 / INT-04 — Cliente HTTP de Pagos contra un servidor simulado.
 *
 * <p>Fija el contrato del OpenAPI del ecosistema v1.4.0, que es el que mas importa
 * verificar: de las integraciones que consumimos es la unica que escribe en otro
 * modulo. Lo que se verifica es el cuerpo de cada llamada, la clave de control que las
 * hace idempotentes y que el estado del pago y el del reembolso no se confundan.
 */
class PagosClientHttpTest {

    private static final String BASE_URL = "http://pagos.test";
    private static final UUID PEDIDO = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CLIENTE = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PAGO = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID MEDIO_PAGO = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final BigDecimal TOTAL = new BigDecimal("18200.00");

    private static final String URL_PAGO = BASE_URL + "/api/v1/payments";
    private static final String URL_REEMBOLSO = URL_PAGO + "/" + PAGO + "/refund";

    private MockRestServiceServer pagos;
    private PagosClientHttp cliente;

    @BeforeEach
    void prepararCliente() {
        RestClient.Builder builder = RestClient.builder();
        pagos = MockRestServiceServer.bindTo(builder).build();
        cliente = new PagosClientHttp(builder, BASE_URL, MEDIO_PAGO);
    }

    /** Solicitud como la arma CU-03: sin codigo de medio de pago. */
    private static SolicitudPagoDto solicitud() {
        return new SolicitudPagoDto(PEDIDO, CLIENTE, null, TOTAL, "ARS", "pedido-" + PEDIDO);
    }

    private static String cuerpoDelPago(String estado) {
        return "{\"codigoPago\": \"" + PAGO + "\", \"codigoPedido\": \"" + PEDIDO
                + "\", \"estado\": \"" + estado + "\", \"monto\": 18200.00, \"moneda\": \"ARS\","
                + " \"medioPago\": {\"tipo\": \"TARJETA_CREDITO\"},"
                + " \"fechaCreacion\": \"2026-11-03T12:00:00Z\"}";
    }

    @Test
    @DisplayName("INT-03: POST del pago con el CrearPagoRequest completo")
    void iniciaElPagoConElCuerpoQueExigePagos() {
        pagos.expect(requestTo(URL_PAGO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.codigoPedido").value(PEDIDO.toString()))
                .andExpect(jsonPath("$.codigoUsuario").value(CLIENTE.toString()))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                // Idempotencia: Pagos la toma del cuerpo, no de una cabecera.
                .andExpect(jsonPath("$.claveControl").value("pedido-" + PEDIDO))
                // Pagos exige el codigo del medio; CU-03 no lo tiene, lo pone el cliente.
                .andExpect(jsonPath("$.codigoMedioPago").value(MEDIO_PAGO.toString()))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cuerpoDelPago("PENDIENTE")));

        PagoDto pago = cliente.iniciarPago(solicitud());

        // El pago nace PENDIENTE: esta llamada no confirma el pedido.
        assertThat(pago.codigoPago()).isEqualTo(PAGO);
        assertThat(pago.codigoPedido()).isEqualTo(PEDIDO);
        assertThat(pago.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.monto()).isEqualByComparingTo(TOTAL);
        pagos.verify();
    }

    @Test
    @DisplayName("INT-03: si la solicitud ya trae medio de pago, se respeta")
    void respetaElMedioDePagoDeLaSolicitud() {
        UUID medioElegido = UUID.fromString("abababab-abab-abab-abab-abababababab");
        pagos.expect(requestTo(URL_PAGO))
                .andExpect(jsonPath("$.codigoMedioPago").value(medioElegido.toString()))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cuerpoDelPago("PENDIENTE")));

        cliente.iniciarPago(new SolicitudPagoDto(
                PEDIDO, CLIENTE, medioElegido, TOTAL, "ARS", "pedido-" + PEDIDO));

        pagos.verify();
    }

    @Test
    @DisplayName("INT-03: el rechazo llega como estado, no como error HTTP")
    void devuelveElRechazoComoEstado() {
        pagos.expect(requestTo(URL_PAGO))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cuerpoDelPago("RECHAZADO")));

        // CU-03 lo traduce a 422 PAYMENT_REJECTED; el cliente solo transporta el estado.
        assertThat(cliente.iniciarPago(solicitud()).estado()).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    @DisplayName("INT-03: un estado fuera del contrato no se interpreta, corta en 503")
    void noInterpretaUnEstadoDesconocido() {
        pagos.expect(requestTo(URL_PAGO))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cuerpoDelPago("EN_REVISION")));

        assertThat(catchThrowable(() -> cliente.iniciarPago(solicitud())))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining(PagosClientHttp.MODULO);
    }

    @Test
    @DisplayName("INT-03: 2xx sin cuerpo no se toma como pago creado")
    void traduceLaRespuestaSinCuerpoA503() {
        pagos.expect(requestTo(URL_PAGO)).andRespond(withSuccess());

        assertThat(catchThrowable(() -> cliente.iniciarPago(solicitud())))
                .isInstanceOf(DependenciaNoDisponibleException.class);
    }

    @Test
    @DisplayName("INT-03: si Pagos no responde, el pedido queda en BORRADOR")
    void traduceElTimeoutA503() {
        pagos.expect(requestTo(URL_PAGO))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(catchThrowable(() -> cliente.iniciarPago(solicitud())))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining(PagosClientHttp.MODULO);
    }

    @Test
    @DisplayName("INT-04: POST del reembolso con monto, motivo y clave prefijada")
    void reembolsaConElCuerpoQueExigePagos() {
        pagos.expect(requestTo(URL_REEMBOLSO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.monto").value(18200.00))
                .andExpect(jsonPath("$.motivo").value(PagosClientHttp.MOTIVO_REEMBOLSO))
                // Prefijo: no puede colisionar con la clave del pago del mismo pedido.
                .andExpect(jsonPath("$.claveControl")
                        .value(PagosClientHttp.PREFIJO_REEMBOLSO + PEDIDO))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"codigoReembolso": "88888888-8888-8888-8888-888888888888",
                                 "codigoPago": "%s", "monto": 18200.00, "estado": "APROBADO",
                                 "montoDisponible": 0.00}
                                """.formatted(PAGO)));

        ReembolsoDto reembolso = cliente.reembolsar(PAGO, PEDIDO, TOTAL);

        // Ojo: APROBADO es el estado del reembolso, no del pago.
        assertThat(reembolso.aprobado()).isTrue();
        assertThat(reembolso.codigoPago()).isEqualTo(PAGO);
        pagos.verify();
    }

    @Test
    @DisplayName("INT-04: un reembolso no aprobado deja el pedido CONFIRMADO")
    void devuelveElReembolsoNoAprobado() {
        pagos.expect(requestTo(URL_REEMBOLSO))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"codigoReembolso": "88888888-8888-8888-8888-888888888888",
                                 "codigoPago": "%s", "monto": 18200.00, "estado": "RECHAZADO",
                                 "montoDisponible": 18200.00}
                                """.formatted(PAGO)));

        assertThat(cliente.reembolsar(PAGO, PEDIDO, TOTAL).aprobado()).isFalse();
    }

    @Test
    @DisplayName("INT-04: si el reembolso falla, el pedido no cambia de estado")
    void traduceElErrorDelReembolsoA503() {
        pagos.expect(requestTo(URL_REEMBOLSO)).andRespond(withServerError());

        assertThat(catchThrowable(() -> cliente.reembolsar(PAGO, PEDIDO, TOTAL)))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining(PagosClientHttp.MODULO);
    }
}
