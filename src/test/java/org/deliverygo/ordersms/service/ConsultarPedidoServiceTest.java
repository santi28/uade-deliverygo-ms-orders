package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PRECIO;
import static org.deliverygo.ordersms.support.PedidosFixture.PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.borrador;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-05 — CU-05 consultar pedido y estado.
 *
 * <p>Verifica que DTO-02 salga con las lineas y con el historial que devuelve REP-04, y
 * que la consulta use REP-03: un pedido ajeno responde igual que uno inexistente.
 */
class ConsultarPedidoServiceTest extends PedidoServiceTestBase {

    private static final UUID CLIENTE_AJENO = UUID.fromString("0f0f0f0f-0f0f-0f0f-0f0f-0f0f0f0f0f0f");

    @Test
    @DisplayName("devuelve el pedido con sus lineas y su historial en orden cronologico")
    void devuelveElPedidoConItemsEHistorial() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.consultarPedido(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.id()).isEqualTo(PEDIDO_ID);
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(respuesta.pagoId()).isEqualTo(PAGO_ID);
        assertThat(respuesta.total()).isEqualByComparingTo(PRECIO);
        assertThat(respuesta.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.productoId()).isEqualTo(PRODUCTO);
                    assertThat(item.cantidad()).isEqualTo(1);
                    // RN-03: el precio que se muestra es el congelado al agregar la linea.
                    assertThat(item.precioUnitario()).isEqualByComparingTo(PRECIO);
                    assertThat(item.subtotal()).isEqualByComparingTo(PRECIO);
                });
        // REP-04: creacion primero, confirmacion despues.
        assertThat(respuesta.historial())
                .extracting(PedidoResponse.HistorialResponse::estadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("un pedido sin lineas se consulta igual, con total cero")
    void devuelveBorradorVacio() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.consultarPedido(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.items()).isEmpty();
        assertThat(respuesta.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.historial()).hasSize(1);
    }

    @Test
    @DisplayName("REP-03: un pedido de otro cliente responde ORDER_NOT_FOUND")
    void rechazaPedidoAjeno() {
        // La consulta filtra por titular: para un cliente ajeno no hay resultado.
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE_AJENO)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.consultarPedido(PEDIDO_ID, CLIENTE_AJENO));

        // Distinguirlo de un pedido inexistente permitiria averiguar que pedidos existen.
        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verify(historialEstadoRepository, never()).findByPedidoIdOrderByFechaCambioAsc(any(UUID.class));
    }

    @Test
    @DisplayName("consultar no toca ninguna integracion")
    void noConsultaIntegraciones() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        service.consultarPedido(PEDIDO_ID, CLIENTE);

        // CU-05 se responde solo con datos propios del modulo.
        verifyNoInteractions(usuariosClient, catalogoClient, pagosClient);
    }
}
