package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.deliverygo.ordersms.dto.ConfirmarPedidoRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.deliverygo.ordersms.support.PedidosFixture.pago;
import static org.deliverygo.ordersms.support.PedidosFixture.producto;
import static org.deliverygo.ordersms.support.PedidosFixture.productoDeOtroComercio;
import static org.deliverygo.ordersms.support.PedidosFixture.productoVendible;
import static org.deliverygo.ordersms.support.PedidosFixture.usuarioInactivo;
import static org.deliverygo.ordersms.support.PedidosFixture.usuarioSinLaDireccion;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-03 — CU-03 confirmar pedido: camino feliz y flujos alternativos.
 *
 * <p>Verifica el orden que impone el CU: primero RN-04, despues que el pedido tenga
 * lineas, despues la revalidacion contra Usuarios (RN-01, RN-06, INT-02) y Catalogo
 * (RN-02, RN-03, RN-05, INT-01), y solo al final el cobro (INT-03). Si algo corta antes,
 * no se cobra.
 *
 * <p>Cubre tambien los dos casos que la Etapa 2 no pudo ejercitar desde la API, porque
 * el mock de Catalogo tiene precios fijos: PRICE_CHANGED y PRODUCT_UNAVAILABLE.
 */
class ConfirmarPedidoServiceTest extends PedidoServiceTestBase {

    private static final ConfirmarPedidoRequest REQUEST = new ConfirmarPedidoRequest("TARJETA_CREDITO");
    private static final UUID CLIENTE_AJENO = UUID.fromString("0f0f0f0f-0f0f-0f0f-0f0f-0f0f0f0f0f0f");
    /** 2 x 8500. */
    private static final BigDecimal TOTAL = new BigDecimal("17000.00");

    @Test
    @DisplayName("con pago APROBADO pasa a CONFIRMADO y guarda el pagoId")
    void confirmaConPagoAprobado() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.APROBADO, TOTAL));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST);

        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
        // DM-09: el pedido guarda el pagoId, no el estado del pago.
        assertThat(respuesta.pagoId()).isEqualTo(PAGO_ID);
        assertThat(respuesta.total()).isEqualByComparingTo(TOTAL);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.CONFIRMADO);
                    assertThat(entrada.motivo()).isEqualTo("CU-03: pago APROBADO " + PAGO_ID);
                });
    }

    @Test
    @DisplayName("INT-03: cobra el total calculado con los precios congelados, no uno del request")
    void cobraElTotalDelPedido() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.APROBADO, TOTAL));
        dadoHistorialPersistido(pedido);

        service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST);

        ArgumentCaptor<SolicitudPagoDto> solicitud = ArgumentCaptor.forClass(SolicitudPagoDto.class);
        verify(pagosClient).iniciarPago(solicitud.capture());
        assertThat(solicitud.getValue().codigoPedido()).isEqualTo(PEDIDO_ID);
        assertThat(solicitud.getValue().codigoUsuario()).isEqualTo(CLIENTE);
        assertThat(solicitud.getValue().monto()).isEqualByComparingTo(TOTAL);
        assertThat(solicitud.getValue().moneda()).isEqualTo(Pedido.MONEDA_POR_DEFECTO);
        // INT-03: la clave de control deriva del pedido, para que reintentar CU-03 no
        // genere dos pagos.
        assertThat(solicitud.getValue().claveControl()).contains(PEDIDO_ID.toString());
    }

    @Test
    @DisplayName("INT-03: con pago PENDIENTE guarda el pagoId y sigue en BORRADOR")
    void conPagoPendienteSigueEnBorradorConElPagoAsociado() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.PENDIENTE, TOTAL));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST);

        // Desvio registrado (03/11): Pagos autoriza en una llamada aparte, asi que
        // iniciar el pago no confirma el pedido. Pasar a CONFIRMADO depende de CU-09.
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.BORRADOR);
        // El pagoId queda guardado: es con lo que CU-09 va a poder identificar el aviso.
        assertThat(respuesta.pagoId()).isEqualTo(PAGO_ID);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.motivo()).contains(PAGO_ID.toString(), "PENDIENTE");
                });
    }

    @Test
    @DisplayName("INT-03: un REEMBOLSADO al iniciar el pago es incumplimiento, no confirma")
    void conEstadoImposibleAlIniciarNoConfirma() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.REEMBOLSADO, TOTAL));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.DEPENDENCY_UNAVAILABLE);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedido.getPagoId()).isNull();
    }

    @Test
    @DisplayName("DM-07: con pago RECHAZADO queda en BORRADOR y el intento va al historial")
    void conPagoRechazadoQuedaEnBorrador() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.RECHAZADO, TOTAL));
        // El rechazo se asienta en una transaccion propia, que vuelve a leer el pedido.
        given(pedidoRepository.findById(PEDIDO_ID)).willReturn(Optional.of(pedido));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PAYMENT_REJECTED);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        // Sin pago aprobado no se asocia ningun pagoId.
        assertThat(pedido.getPagoId()).isNull();
        assertThat(pedido.getHistorial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.getEstadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.getEstadoNuevo()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.getMotivo()).isEqualTo("CU-03: pago RECHAZADO " + PAGO_ID);
                });
        verify(pedidoRepository).save(pedido);
    }

    @Test
    @DisplayName("un estado de pago que no resuelve el cobro no confirma nada (503)")
    void rechazaEstadoDePagoInesperado() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        // El contrato con Eq. 4 dice que el cobro se resuelve en la misma llamada.
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willReturn(pago(EstadoPago.REEMBOLSADO, TOTAL));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.DEPENDENCY_UNAVAILABLE);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedido.getPagoId()).isNull();
    }

    @Test
    @DisplayName("INT-03: si Pagos no responde el pedido sigue en BORRADOR (503)")
    void propagaPagosCaido() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(pagosClient.iniciarPago(any(SolicitudPagoDto.class)))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        // Nunca se confirma sin cobro.
        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedido.getPagoId()).isNull();
    }

    @Test
    @DisplayName("RN-03: si el precio cambio corta con PRICE_CHANGED y no cobra")
    void rechazaPrecioCambiado() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, new BigDecimal("9900.00"), true)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRICE_CHANGED);
        assertThat(error).hasMessageContaining(PRECIO.toPlainString());
        // El cliente tiene que ver el total nuevo antes de pagar.
        verifyNoInteractions(pagosClient);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("RN-05: un producto que quedo sin stock corta con PRODUCT_UNAVAILABLE")
    void rechazaProductoNoDisponible() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, PRECIO, false)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRODUCT_UNAVAILABLE);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("RN-05: un producto que ya no esta en Catalogo corta con PRODUCT_UNAVAILABLE")
    void rechazaProductoQueYaNoEsta() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRODUCT_UNAVAILABLE);
        assertThat(error).hasMessageContaining("ya no esta en Catalogo");
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("RN-02: un producto que paso a otro comercio corta con MIXED_MERCHANT")
    void rechazaProductoQuePasoDeComercio() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(productoDeOtroComercio(PRODUCTO)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.MIXED_MERCHANT);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("un pedido sin lineas no se confirma, y no se revalida nada")
    void rechazaPedidoSinLineas() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borrador()));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no tiene items");
        verifyNoInteractions(usuariosClient, catalogoClient, pagosClient);
    }

    @Test
    @DisplayName("RN-04: un pedido ya confirmado no se vuelve a confirmar, y no se cobra dos veces")
    void rechazaPedidoYaConfirmado() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID))
                .willReturn(Optional.of(enEstado(EstadoPedido.CONFIRMADO, PAGO_ID)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        // RN-04 se valida antes de cobrar.
        verifyNoInteractions(usuariosClient, catalogoClient, pagosClient);
    }

    @Test
    @DisplayName("RN-04: un pedido cancelado no se confirma")
    void rechazaPedidoCancelado() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID))
                .willReturn(Optional.of(enEstado(EstadoPedido.CANCELADO, null)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("RN-01: un cliente que se dio de baja con el borrador abierto no confirma")
    void rechazaClienteInactivo() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioInactivo()));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        verifyNoInteractions(catalogoClient, pagosClient);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("RN-06: una direccion que el cliente borro mientras el borrador estaba abierto no confirma")
    void rechazaDireccionQueYaNoEsDelCliente() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioSinLaDireccion()));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no pertenece al cliente");
        verifyNoInteractions(catalogoClient, pagosClient);
    }

    @Test
    @DisplayName("INT-02: si Usuarios no responde no se confirma (503)")
    void propagaUsuariosCaido() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(usuariosClient.buscarUsuario(CLIENTE))
                .willThrow(new DependenciaNoDisponibleException("Usuarios", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("INT-01: si Catalogo no responde no se cobra (503)")
    void propagaCatalogoCaido() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoClienteActivo();
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willThrow(new DependenciaNoDisponibleException("Catalogo", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        // Nunca se usa un precio supuesto para cobrar.
        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("REP-02: un pedido de otro cliente responde ORDER_NOT_FOUND")
    void rechazaPedidoAjeno() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borradorConItem(1)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE_AJENO, REQUEST));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(pagosClient);
    }

    @Test
    @DisplayName("un pedido inexistente responde ORDER_NOT_FOUND")
    void rechazaPedidoInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.confirmarPedido(PEDIDO_ID, CLIENTE, REQUEST));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(pagosClient);
    }
}
