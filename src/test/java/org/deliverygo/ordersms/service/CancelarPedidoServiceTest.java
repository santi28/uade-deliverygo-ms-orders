package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.REEMBOLSO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.deliverygo.ordersms.support.PedidosFixture.pago;
import static org.deliverygo.ordersms.support.PedidosFixture.reembolsoAprobado;
import static org.deliverygo.ordersms.support.PedidosFixture.reembolsoRechazado;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-04 — CU-04 cancelar pedido: camino feliz y flujos alternativos.
 *
 * <p>Un borrador se cancela sin tocar Pagos. Un pedido CONFIRMADO solo pasa a CANCELADO
 * si INT-04 responde REEMBOLSADO: cualquier otra respuesta lo deja como estaba, para no
 * cancelar un pedido ya cobrado sin devolver la plata.
 */
class CancelarPedidoServiceTest extends PedidoServiceTestBase {

    @Test
    @DisplayName("un borrador se cancela sin reembolso y sin consultar Pagos")
    void cancelaBorradorSinReembolso() {
        Pedido pedido = borradorConItem(1);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.cancelarPedido(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.CANCELADO);
                    assertThat(entrada.motivo()).isEqualTo("CU-04: cancelado desde BORRADOR, sin reembolso");
                });
        // No hubo cobro: no hay nada que devolver.
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("INT-04: un pedido CONFIRMADO se cancela cuando Pagos responde REEMBOLSADO")
    void cancelaConfirmadoConReembolso() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        given(pagosClient.reembolsar(PAGO_ID, PEDIDO_ID, pedido.getTotal()))
                .willReturn(reembolsoAprobado(pedido.getTotal()));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.cancelarPedido(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.CONFIRMADO);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.CANCELADO);
                    assertThat(entrada.motivo()).isEqualTo("CU-04: cancelado con reembolso del pago " + PAGO_ID);
                });
        verify(pagosClient).reembolsar(PAGO_ID, PEDIDO_ID, pedido.getTotal());
    }

    @Test
    @DisplayName("si Pagos no reembolsa, el pedido sigue CONFIRMADO")
    void noCancelaSinReembolso() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        given(pagosClient.reembolsar(PAGO_ID, PEDIDO_ID, pedido.getTotal()))
                .willReturn(reembolsoRechazado(pedido.getTotal()));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_REJECTED);
        assertThat(error).hasMessageContaining("sigue CONFIRMADO");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("un pago que sigue APROBADO tampoco habilita la cancelacion")
    void noCancelaConPagoTodaviaAprobado() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        given(pagosClient.reembolsar(PAGO_ID, PEDIDO_ID, pedido.getTotal()))
                .willReturn(new ReembolsoDto(REEMBOLSO_ID, PAGO_ID, pedido.getTotal(), "PENDIENTE", pedido.getTotal()));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        // Solo un reembolso APROBADO habilita pasar a CANCELADO.
        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_REJECTED);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("INT-04: si Pagos no responde el pedido queda como estaba (503)")
    void propagaPagosCaido() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        given(pagosClient.reembolsar(PAGO_ID, PEDIDO_ID, pedido.getTotal()))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("un pedido CONFIRMADO sin pago asociado no se cancela a ciegas")
    void rechazaConfirmadoSinPago() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, null);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        // Es inconsistencia de datos: CU-03 confirma y asocia el pago en la misma transaccion.
        assertThat(codigoDe(error)).isEqualTo(CodigoError.ORDER_INVALID_STATE);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("RN-04: un pedido ya cancelado no se vuelve a cancelar, y no se pide otro reembolso")
    void rechazaPedidoYaCancelado() {
        Pedido pedido = enEstado(EstadoPedido.CANCELADO, null);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        // RN-04 se valida antes de pedir el reembolso.
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("REP-03: un pedido inexistente o de otro cliente responde ORDER_NOT_FOUND")
    void rechazaPedidoInexistenteOAjeno() {
        // REP-03 filtra por titular en la consulta: los dos casos llegan igual al service.
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(pagosClient);
    }
}
