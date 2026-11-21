package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.mapper.PedidoMapper;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.DIRECCION;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.conId;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * SRV-08 — CU-08 registrar transicion e historial.
 *
 * <p>CU-08 no tiene API: lo ejercitan los demas CU. Lo que se verifica aca es lo propio
 * de SRV-08: que todo cambio de estado pase por la maquina de estados (tambien el
 * nacimiento del pedido en CU-01), que estado e historial queden consistentes, y que una
 * transicion invalida no toque ninguno de los dos. La maquina de estados es un spy: se
 * usa de verdad y ademas se verifica que se la llamo.
 */
class RegistrarTransicionServiceTest extends PedidoServiceTestBase {

    private MaquinaEstados maquina;

    @BeforeEach
    void espiarMaquinaDeEstados() {
        maquina = spy(new MaquinaEstados());
        service = new PedidoService(pedidoRepository, historialEstadoRepository, usuariosClient, catalogoClient,
                pagosClient, logisticaClient, maquina, new PedidoMapper(), transactionManager);
    }

    @Test
    @DisplayName("CU-01: el nacimiento del pedido tambien pasa por la maquina de estados")
    void creacionPasaPorLaMaquina() {
        dadoClienteActivo();
        given(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR)).willReturn(Optional.empty());
        given(pedidoRepository.save(any(Pedido.class)))
                .willAnswer(invocacion -> conId(invocacion.getArgument(0), PEDIDO_ID));

        service.crearPedido(CLIENTE, new CrearPedidoRequest(COMERCIO, DIRECCION));

        verify(maquina).transicionar(any(Pedido.class), eq(EstadoPedido.BORRADOR), eq("Pedido creado como borrador"));
    }

    @Test
    @DisplayName("la ultima entrada del historial coincide con el estado actual del pedido")
    void historialConsistenteConElEstado() {
        Pedido pedido = borradorConItem(1);
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        service.cancelarPedido(PEDIDO_ID, CLIENTE);

        verify(maquina).transicionar(eq(pedido), eq(EstadoPedido.CANCELADO), anyString());
        HistorialEstado ultima = pedido.getHistorial().get(pedido.getHistorial().size() - 1);
        assertThat(ultima.getEstadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(ultima.getEstadoNuevo()).isEqualTo(pedido.getEstado());
        assertThat(ultima.getFechaCambio()).isNotNull();
        assertThat(ultima.getMotivo()).startsWith("CU-04");
    }

    @Test
    @DisplayName("RN-04: una transicion invalida no cambia el estado ni deja entrada en el historial")
    void transicionInvalidaNoTocaNada() {
        Pedido pedido = enEstado(EstadoPedido.CANCELADO, null);
        List<HistorialEstado> antes = List.copyOf(pedido.getHistorial());
        given(pedidoRepository.findByIdAndClienteId(PEDIDO_ID, CLIENTE)).willReturn(Optional.of(pedido));

        Throwable error = catchThrowable(() -> service.cancelarPedido(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getHistorial()).containsExactlyElementsOf(antes);
        verify(maquina, never()).transicionar(any(), any(), anyString());
    }
}
