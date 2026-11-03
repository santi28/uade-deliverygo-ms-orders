package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.service.PedidoService.ResultadoCreacionPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.DIRECCION;
import static org.deliverygo.ordersms.support.PedidosFixture.OTRO_COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.borrador;
import static org.deliverygo.ordersms.support.PedidosFixture.conId;
import static org.deliverygo.ordersms.support.PedidosFixture.usuarioInactivo;
import static org.deliverygo.ordersms.support.PedidosFixture.usuarioSinLaDireccion;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-01 — CU-01 crear pedido o borrador: camino feliz y flujos alternativos.
 *
 * <p>Cubre RN-01 y RN-06 por INT-02, la reutilizacion del borrador abierto (REP-01) y
 * RN-02 cuando el borrador abierto es de otro comercio.
 */
class CrearPedidoServiceTest extends PedidoServiceTestBase {

    private static final CrearPedidoRequest REQUEST = new CrearPedidoRequest(COMERCIO, DIRECCION);

    @Test
    @DisplayName("crea un borrador vacio y asienta su nacimiento en el historial")
    void creaBorradorVacio() {
        dadoClienteActivo();
        given(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR))
                .willReturn(Optional.empty());
        // Emula lo que hace JPA al persistir: devuelve el agregado ya con id.
        given(pedidoRepository.save(any(Pedido.class)))
                .willAnswer(invocacion -> conId(invocacion.getArgument(0), PEDIDO_ID));

        ResultadoCreacionPedido resultado = service.crearPedido(CLIENTE, REQUEST);

        assertThat(resultado.creado()).isTrue();
        PedidoResponse pedido = resultado.pedido();
        assertThat(pedido.id()).isEqualTo(PEDIDO_ID);
        assertThat(pedido.estado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedido.clienteId()).isEqualTo(CLIENTE);
        assertThat(pedido.comercioId()).isEqualTo(COMERCIO);
        assertThat(pedido.direccionEntregaId()).isEqualTo(DIRECCION);
        assertThat(pedido.pagoId()).isNull();
        // El pedido nace sin lineas y con total cero: las lineas las carga CU-02.
        assertThat(pedido.items()).isEmpty();
        assertThat(pedido.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pedido.moneda()).isEqualTo(Pedido.MONEDA_POR_DEFECTO);
        // REL-02: el historial que lee CU-05 arranca con la creacion.
        assertThat(pedido.historial()).singleElement()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.motivo()).isEqualTo("Pedido creado como borrador");
                });
    }

    @Test
    @DisplayName("REP-01: devuelve el borrador abierto del mismo comercio sin crear otro")
    void reutilizaElBorradorAbierto() {
        Pedido abierto = borrador();
        dadoClienteActivo();
        given(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR))
                .willReturn(Optional.of(abierto));
        dadoHistorialPersistido(abierto);

        ResultadoCreacionPedido resultado = service.crearPedido(CLIENTE, REQUEST);

        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.pedido().id()).isEqualTo(PEDIDO_ID);
        // Reintentar CU-01 no deja borradores huerfanos ni duplica el historial.
        verify(pedidoRepository, never()).save(any(Pedido.class));
        assertThat(abierto.getHistorial()).hasSize(1);
    }

    @Test
    @DisplayName("RN-02: un borrador abierto de otro comercio corta con MIXED_MERCHANT")
    void rechazaBorradorAbiertoDeOtroComercio() {
        dadoClienteActivo();
        given(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR))
                .willReturn(Optional.of(borrador()));

        Throwable error = catchThrowable(
                () -> service.crearPedido(CLIENTE, new CrearPedidoRequest(OTRO_COMERCIO, DIRECCION)));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.MIXED_MERCHANT);
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("RN-01: un cliente que no esta en Usuarios no abre pedidos")
    void rechazaClienteInexistente() {
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.crearPedido(CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no esta registrado");
        // INT-02 se valida antes de tocar la base.
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("RN-01: un cliente dado de baja no abre pedidos")
    void rechazaClienteInactivo() {
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioInactivo()));

        Throwable error = catchThrowable(() -> service.crearPedido(CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no esta activo");
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("RN-06: la direccion de entrega tiene que ser del cliente")
    void rechazaDireccionAjena() {
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioSinLaDireccion()));

        Throwable error = catchThrowable(() -> service.crearPedido(CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no pertenece al cliente");
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("INT-02: si Usuarios no responde el pedido no se crea (503)")
    void propagaUsuariosCaido() {
        given(usuariosClient.buscarUsuario(CLIENTE))
                .willThrow(new DependenciaNoDisponibleException("Usuarios", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.crearPedido(CLIENTE, REQUEST));

        // El 503 lo arma el GlobalExceptionHandler; el service no inventa un usuario valido.
        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("guarda el pedido con el cliente autenticado, no con uno del cuerpo")
    void guardaElPedidoConElClienteAutenticado() {
        dadoClienteActivo();
        given(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR))
                .willReturn(Optional.empty());
        given(pedidoRepository.save(any(Pedido.class)))
                .willAnswer(invocacion -> conId(invocacion.getArgument(0), PEDIDO_ID));

        service.crearPedido(CLIENTE, REQUEST);

        ArgumentCaptor<Pedido> guardado = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getClienteId()).isEqualTo(CLIENTE);
        assertThat(guardado.getValue().getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }
}
