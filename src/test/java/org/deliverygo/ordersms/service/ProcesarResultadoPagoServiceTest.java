package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.dto.ResultadoPagoRequest;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.deliverygo.ordersms.support.PedidosFixture.pago;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-09 — CU-09 procesar resultado del pago: camino feliz y flujos alternativos.
 *
 * <p>El aviso solo identifica el pago; lo que se aplica es lo que responde Pagos por
 * INT-05. Los casos cubren la tabla de PLAN.md (Etapa 6): estado del pago por estado del
 * pedido, correlacion e idempotencia.
 */
class ProcesarResultadoPagoServiceTest extends PedidoServiceTestBase {

    private static final ResultadoPagoRequest AVISO = new ResultadoPagoRequest(PAGO_ID);

    private Pedido dadoPedido(EstadoPedido estado) {
        Pedido pedido = enEstado(estado, PAGO_ID);
        given(pedidoRepository.findById(PEDIDO_ID)).willReturn(Optional.of(pedido));
        return pedido;
    }

    private void dadoPagoEnPagos(Pedido pedido, EstadoPago estado) {
        given(pagosClient.consultarPago(PAGO_ID)).willReturn(Optional.of(pago(estado, pedido.getTotal())));
    }

    @Test
    @DisplayName("INT-05: un pago APROBADO pasa el borrador a CONFIRMADO y lo asienta en el historial")
    void aprobadoConfirmaElBorrador() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        dadoHistorialPersistido(pedido);
        dadoPagoEnPagos(pedido, EstadoPago.APROBADO);

        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.CONFIRMADO);
                    assertThat(entrada.motivo()).isEqualTo("CU-09: pago APROBADO " + PAGO_ID);
                });
    }

    @Test
    @DisplayName("un aviso repetido sobre un CONFIRMADO no consulta a Pagos ni deja otra entrada")
    void confirmadoEsIdempotente() {
        Pedido pedido = dadoPedido(EstadoPedido.CONFIRMADO);
        dadoHistorialPersistido(pedido);
        int entradas = pedido.getHistorial().size();

        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(respuesta.historial()).hasSize(entradas);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("RN-04: un pago APROBADO no reabre un pedido CANCELADO")
    void aprobadoNoReabreCancelado() {
        Pedido pedido = dadoPedido(EstadoPedido.CANCELADO);
        dadoPagoEnPagos(pedido, EstadoPago.APROBADO);

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("con el interruptor de RN-04 prendido, un APROBADO confirma un CANCELADO con el motivo marcado")
    void interruptorConfirmaCancelado() {
        dadoElInterruptorDeRn04Prendido();
        Pedido pedido = dadoPedido(EstadoPedido.CANCELADO);
        dadoHistorialPersistido(pedido);
        dadoPagoEnPagos(pedido, EstadoPago.APROBADO);

        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> assertThat(entrada.motivo()).contains("RN-04 salteada"));
    }

    @Test
    @DisplayName("un pago RECHAZADO deja el borrador y asienta el rechazo una sola vez")
    void rechazadoSeAsientaUnaVez() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        dadoHistorialPersistido(pedido);
        dadoPagoEnPagos(pedido, EstadoPago.RECHAZADO);
        int entradas = pedido.getHistorial().size();

        service.procesarResultadoPago(PEDIDO_ID, AVISO);
        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(respuesta.historial()).hasSize(entradas + 1);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.motivo()).isEqualTo("CU-09: pago RECHAZADO " + PAGO_ID);
                });
    }

    @Test
    @DisplayName("un pago PENDIENTE no cambia nada")
    void pendienteNoCambiaNada() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        dadoHistorialPersistido(pedido);
        dadoPagoEnPagos(pedido, EstadoPago.PENDIENTE);
        int entradas = pedido.getHistorial().size();

        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(respuesta.historial()).hasSize(entradas);
    }

    @Test
    @DisplayName("un RECHAZADO tardio sobre un CANCELADO no cambia nada")
    void rechazadoSobreCanceladoNoCambiaNada() {
        Pedido pedido = dadoPedido(EstadoPedido.CANCELADO);
        dadoHistorialPersistido(pedido);
        dadoPagoEnPagos(pedido, EstadoPago.RECHAZADO);
        int entradas = pedido.getHistorial().size();

        PedidoResponse respuesta = service.procesarResultadoPago(PEDIDO_ID, AVISO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(respuesta.historial()).hasSize(entradas);
    }

    @Test
    @DisplayName("un pedido inexistente responde ORDER_NOT_FOUND")
    void pedidoInexistente() {
        given(pedidoRepository.findById(PEDIDO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("un pedido sin pago iniciado responde ORDER_INVALID_STATE")
    void pedidoSinPago() {
        given(pedidoRepository.findById(PEDIDO_ID)).willReturn(Optional.of(borradorConItem(1)));

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.ORDER_INVALID_STATE);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("un aviso de otro pago responde VALIDATION_ERROR sin consultar a Pagos")
    void avisoDeOtroPago() {
        dadoPedido(EstadoPedido.BORRADOR);

        Throwable error = catchThrowable(() ->
                service.procesarResultadoPago(PEDIDO_ID, new ResultadoPagoRequest(UUID.randomUUID())));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("INT-05: un pago que Pagos no conoce responde PAYMENT_MISMATCH")
    void pagoDesconocido() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        given(pagosClient.consultarPago(PAGO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_MISMATCH);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("un APROBADO por otro importe no confirma: el borrador se edito despues de iniciar el pago")
    void importeDistintoNoConfirma() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        given(pagosClient.consultarPago(PAGO_ID))
                .willReturn(Optional.of(pago(EstadoPago.APROBADO, pedido.getTotal().add(BigDecimal.ONE))));

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_MISMATCH);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("un APROBADO informado para otro pedido no confirma")
    void otroPedidoNoConfirma() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        given(pagosClient.consultarPago(PAGO_ID)).willReturn(Optional.of(new PagoDto(
                PAGO_ID, UUID.randomUUID(), EstadoPago.APROBADO, pedido.getTotal(), pedido.getMoneda())));

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_MISMATCH);
    }

    @Test
    @DisplayName("un APROBADO en otra moneda no confirma")
    void otraMonedaNoConfirma() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        given(pagosClient.consultarPago(PAGO_ID)).willReturn(Optional.of(new PagoDto(
                PAGO_ID, PEDIDO_ID, EstadoPago.APROBADO, pedido.getTotal(), "USD")));

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_MISMATCH);
    }

    @Test
    @DisplayName("si Pagos no responde sale 503 y el pedido queda como estaba")
    void pagosCaido() {
        Pedido pedido = dadoPedido(EstadoPedido.BORRADOR);
        given(pagosClient.consultarPago(PAGO_ID))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RestClientException("timeout")));

        Throwable error = catchThrowable(() -> service.procesarResultadoPago(PEDIDO_ID, AVISO));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }
}
