package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.dto.EntregaDto;
import org.deliverygo.ordersms.client.dto.SolicitudEntregaDto;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.DIRECCION;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-10 — CU-10 solicitar asignacion logistica: camino feliz y flujos alternativos.
 *
 * <p>Logistica (INT-06) esta mockeada: lo que se verifica es que solo un CONFIRMADO pida
 * la entrega, que el resultado quede en el historial y que una falla de Logistica no
 * desconfirme el pedido.
 */
class SolicitarAsignacionLogisticaServiceTest extends PedidoServiceTestBase {

    private Pedido dadoPedido(EstadoPedido estado) {
        Pedido pedido = enEstado(estado, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        return pedido;
    }

    @Test
    @DisplayName("INT-06: un pedido CONFIRMADO pasa a EN_PREPARACION con la entrega en el historial")
    void confirmadoPasaAEnPreparacion() {
        Pedido pedido = dadoPedido(EstadoPedido.CONFIRMADO);
        dadoHistorialPersistido(pedido);
        given(logisticaClient.solicitarAsignacion(any(SolicitudEntregaDto.class)))
                .willReturn(new EntregaDto(42L, "ASIGNADA"));

        PedidoResponse respuesta = service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.EN_PREPARACION);
        assertThat(respuesta.historial()).last().satisfies(entrada -> {
            assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.CONFIRMADO);
            assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.EN_PREPARACION);
            assertThat(entrada.motivo()).isEqualTo("CU-10: entrega 42 ASIGNADA en Logistica");
        });
        verify(logisticaClient).solicitarAsignacion(new SolicitudEntregaDto(PEDIDO_ID, CLIENTE, COMERCIO, DIRECCION));
    }

    @Test
    @DisplayName("sin repartidores (PENDIENTE_ASIGNACION) la entrega igual existe: pasa a EN_PREPARACION")
    void pendienteDeAsignacionTambienPasa() {
        Pedido pedido = dadoPedido(EstadoPedido.CONFIRMADO);
        dadoHistorialPersistido(pedido);
        given(logisticaClient.solicitarAsignacion(any(SolicitudEntregaDto.class)))
                .willReturn(new EntregaDto(43L, "PENDIENTE_ASIGNACION"));

        PedidoResponse respuesta = service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.EN_PREPARACION);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> assertThat(entrada.motivo()).contains("PENDIENTE_ASIGNACION"));
    }

    @Test
    @DisplayName("una solicitud repetida no vuelve a llamar a Logistica ni deja otra entrada")
    void solicitudRepetida() {
        Pedido pedido = dadoPedido(EstadoPedido.CONFIRMADO);
        pedido.registrarTransicion(EstadoPedido.EN_PREPARACION, "CU-10: entrega 42 ASIGNADA en Logistica");
        dadoHistorialPersistido(pedido);
        int entradas = pedido.getHistorial().size();

        PedidoResponse respuesta = service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.EN_PREPARACION);
        assertThat(respuesta.historial()).hasSize(entradas);
        verifyNoInteractions(logisticaClient);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"BORRADOR", "CANCELADO"})
    @DisplayName("RN-04: solo un pedido CONFIRMADO pide la entrega")
    void soloConfirmado(EstadoPedido estado) {
        Pedido pedido = dadoPedido(estado);

        Throwable error = catchThrowable(() -> service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        assertThat(pedido.getEstado()).isEqualTo(estado);
        verifyNoInteractions(logisticaClient);
    }

    @Test
    @DisplayName("si Logistica no responde sale 503 y el pedido sigue CONFIRMADO")
    void logisticaCaida() {
        Pedido pedido = dadoPedido(EstadoPedido.CONFIRMADO);
        int entradas = pedido.getHistorial().size();
        given(logisticaClient.solicitarAsignacion(any(SolicitudEntregaDto.class)))
                .willThrow(new DependenciaNoDisponibleException("Logistica", new RestClientException("timeout")));

        Throwable error = catchThrowable(() -> service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(pedido.getHistorial()).hasSize(entradas);
    }

    @Test
    @DisplayName("REP-03: un pedido inexistente o de otro cliente responde ORDER_NOT_FOUND")
    void pedidoInexistente() {
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.solicitarAsignacionLogistica(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(logisticaClient);
    }
}
