package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.deliverygo.ordersms.dto.AgregarItemsPedidoRequest;
import org.deliverygo.ordersms.dto.AgregarItemsPedidoRequest.LineaItem;
import org.deliverygo.ordersms.dto.ConfirmarPedidoRequest;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.COMERCIO;
import static org.deliverygo.ordersms.support.PedidosFixture.DIRECCION;
import static org.deliverygo.ordersms.support.PedidosFixture.OTRO_PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PRECIO;
import static org.deliverygo.ordersms.support.PedidosFixture.PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.pago;
import static org.deliverygo.ordersms.support.PedidosFixture.producto;
import static org.deliverygo.ordersms.support.PedidosFixture.productoVendible;
import static org.deliverygo.ordersms.support.PedidosFixture.usuarioActivo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Rollback de CU-02, CU-03 y CU-04 contra la base.
 *
 * <p>Es el complemento de los tests de Service: alla los repositorios estan mockeados,
 * asi que se verifica que el CU corte con el codigo correcto, pero no que la base quede
 * limpia. Aca el CU corre con su {@code @Transactional} real y despues se vuelve a leer
 * la base en transacciones nuevas.
 *
 * <p>El test no lleva {@code @Transactional} a proposito: envolverlo en una transaccion
 * propia haria que los commits del CU no se vieran y que el rollback que se quiere
 * observar no existiera.
 *
 * <p>Lo unico mockeado son las integraciones (INT-01 Catalogo, INT-02 Usuarios,
 * INT-03/04 Pagos), que es lo que tiene que fallar para provocar cada rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
class PedidoServiceRollbackTest {

    private static final ConfirmarPedidoRequest CONFIRMAR = new ConfirmarPedidoRequest("TARJETA_CREDITO");
    /** 2 x 8500. */
    private static final BigDecimal TOTAL = new BigDecimal("17000.00");

    @Autowired
    private PedidoService service;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private HistorialEstadoRepository historialEstadoRepository;

    @MockitoBean
    private UsuariosClient usuariosClient;

    @MockitoBean
    private CatalogoClient catalogoClient;

    @MockitoBean
    private PagosClient pagosClient;

    @BeforeEach
    @AfterEach
    void limpiarLaBase() {
        // Los items y el historial se van por cascada desde la raiz del agregado (REL-01, REL-02).
        pedidoRepository.deleteAll();
    }

    /** Borrador persistido con una linea de PRODUCTO, como lo deja CU-01 mas CU-02. */
    private Pedido darBorradorPersistido(int cantidad) {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.registrarTransicion(EstadoPedido.BORRADOR, "Pedido creado como borrador");
        pedido.agregarItem(PRODUCTO, "Pizza muzzarella", cantidad, PRECIO);
        return pedidoRepository.save(pedido);
    }

    /** Pedido ya cobrado, como lo deja CU-03 con pago APROBADO. */
    private Pedido darPedidoConfirmadoPersistido() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.registrarTransicion(EstadoPedido.BORRADOR, "Pedido creado como borrador");
        pedido.agregarItem(PRODUCTO, "Pizza muzzarella", 2, PRECIO);
        pedido.registrarTransicion(EstadoPedido.CONFIRMADO, "CU-03: pago APROBADO " + PAGO_ID);
        pedido.asociarPago(PAGO_ID);
        return pedidoRepository.save(pedido);
    }

    private Pedido leerDeLaBase(UUID pedidoId) {
        return pedidoRepository.findById(pedidoId).orElseThrow();
    }

    /**
     * REP-02 — Lectura para los casos que miran las lineas: {@code getItems()} es LAZY y
     * fuera de la transaccion del repositorio ya no hay sesion que lo resuelva (DM-02).
     */
    private Pedido leerConItemsDeLaBase(UUID pedidoId) {
        return pedidoRepository.findByIdConItems(pedidoId).orElseThrow();
    }

    private List<HistorialEstado> leerHistorialDeLaBase(UUID pedidoId) {
        return historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(pedidoId);
    }

    private void dadoClienteYProductoVendibles() {
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioActivo()));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
    }

    // ----------------------------------------------------------------- CU-03 confirmar

    @Test
    @DisplayName("CU-03: con pago APROBADO quedan persistidos el estado, el pagoId y el historial")
    void confirmarConPagoAprobadoPersiste() {
        Pedido pedido = darBorradorPersistido(2);
        dadoClienteYProductoVendibles();
        given(pagosClient.cobrar(any(SolicitudPagoDto.class))).willReturn(pago(EstadoPago.APROBADO, TOTAL));

        service.confirmarPedido(pedido.getId(), CLIENTE, CONFIRMAR);

        // Control del test: si esto no quedara persistido, los casos de rollback no probarian nada.
        Pedido enLaBase = leerDeLaBase(pedido.getId());
        assertThat(enLaBase.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(enLaBase.getPagoId()).isEqualTo(PAGO_ID);
        assertThat(leerHistorialDeLaBase(pedido.getId()))
                .extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("CU-03, DM-07: con pago RECHAZADO se deshace la confirmacion pero el intento queda asentado")
    void confirmarConPagoRechazadoDeshaceTodoMenosElHistorial() {
        Pedido pedido = darBorradorPersistido(2);
        dadoClienteYProductoVendibles();
        given(pagosClient.cobrar(any(SolicitudPagoDto.class))).willReturn(pago(EstadoPago.RECHAZADO, TOTAL));

        Throwable error = catchThrowable(() -> service.confirmarPedido(pedido.getId(), CLIENTE, CONFIRMAR));

        assertThat(error).isInstanceOf(ReglaNegocioException.class);
        Pedido enLaBase = leerDeLaBase(pedido.getId());
        assertThat(enLaBase.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(enLaBase.getPagoId()).isNull();
        // La entrada del rechazo va en una transaccion propia (REQUIRES_NEW): sobrevive al
        // rollback del CU, que es lo que pide DM-07.
        assertThat(leerHistorialDeLaBase(pedido.getId()))
                .extracting(HistorialEstado::getEstadoNuevo, HistorialEstado::getMotivo)
                .containsExactly(
                        tuple(EstadoPedido.BORRADOR, "Pedido creado como borrador"),
                        tuple(EstadoPedido.BORRADOR, "CU-03: pago RECHAZADO " + PAGO_ID));
    }

    @Test
    @DisplayName("CU-03, RN-03: si el precio cambio no queda nada escrito y no se cobra")
    void confirmarConPrecioCambiadoNoDejaRastro() {
        Pedido pedido = darBorradorPersistido(2);
        given(usuariosClient.buscarUsuario(CLIENTE)).willReturn(Optional.of(usuarioActivo()));
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, new BigDecimal("9900.00"), true)));

        Throwable error = catchThrowable(() -> service.confirmarPedido(pedido.getId(), CLIENTE, CONFIRMAR));

        assertThat(error).hasMessageContaining("cambio de");
        Pedido enLaBase = leerConItemsDeLaBase(pedido.getId());
        assertThat(enLaBase.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(enLaBase.getPagoId()).isNull();
        // El precio congelado de la linea sigue siendo el de cuando se agrego (RN-03).
        assertThat(enLaBase.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getPrecioUnitario()).isEqualByComparingTo(PRECIO));
        // Corta antes de cobrar, asi que no hay intento que asentar.
        assertThat(leerHistorialDeLaBase(pedido.getId())).hasSize(1);
    }

    @Test
    @DisplayName("CU-03, INT-03: si Pagos no responde el pedido queda intacto en BORRADOR")
    void confirmarConPagosCaidoNoDejaRastro() {
        Pedido pedido = darBorradorPersistido(2);
        dadoClienteYProductoVendibles();
        given(pagosClient.cobrar(any(SolicitudPagoDto.class)))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.confirmarPedido(pedido.getId(), CLIENTE, CONFIRMAR));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        Pedido enLaBase = leerDeLaBase(pedido.getId());
        assertThat(enLaBase.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(enLaBase.getPagoId()).isNull();
        // Un timeout no es un rechazo: no se asienta ningun intento.
        assertThat(leerHistorialDeLaBase(pedido.getId())).hasSize(1);
    }

    // ------------------------------------------------------------------ CU-04 cancelar

    @Test
    @DisplayName("CU-04: la cancelacion de un borrador queda persistida")
    void cancelarBorradorPersiste() {
        Pedido pedido = darBorradorPersistido(1);

        service.cancelarPedido(pedido.getId(), CLIENTE);

        assertThat(leerDeLaBase(pedido.getId()).getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(leerHistorialDeLaBase(pedido.getId()))
                .extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("CU-04, INT-04: si Pagos no reembolsa el pedido sigue CONFIRMADO en la base")
    void cancelarSinReembolsoNoCambiaElEstado() {
        Pedido pedido = darPedidoConfirmadoPersistido();
        given(pagosClient.reembolsar(PAGO_ID, pedido.getId()))
                .willReturn(pago(EstadoPago.RECHAZADO, TOTAL));

        Throwable error = catchThrowable(() -> service.cancelarPedido(pedido.getId(), CLIENTE));

        assertThat(error).hasMessageContaining("sigue CONFIRMADO");
        Pedido enLaBase = leerDeLaBase(pedido.getId());
        assertThat(enLaBase.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        // El pago sigue asociado: no se perdio la referencia al cobro que no se devolvio.
        assertThat(enLaBase.getPagoId()).isEqualTo(PAGO_ID);
        assertThat(leerHistorialDeLaBase(pedido.getId()))
                .extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("CU-04, INT-04: si Pagos no responde el pedido queda como estaba")
    void cancelarConPagosCaidoNoCambiaElEstado() {
        Pedido pedido = darPedidoConfirmadoPersistido();
        given(pagosClient.reembolsar(PAGO_ID, pedido.getId()))
                .willThrow(new DependenciaNoDisponibleException("Pagos", new RuntimeException("timeout")));

        Throwable error = catchThrowable(() -> service.cancelarPedido(pedido.getId(), CLIENTE));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(leerDeLaBase(pedido.getId()).getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(leerHistorialDeLaBase(pedido.getId())).hasSize(2);
    }

    @Test
    @DisplayName("CU-04: la cancelacion con reembolso REEMBOLSADO queda persistida")
    void cancelarConReembolsoPersiste() {
        Pedido pedido = darPedidoConfirmadoPersistido();
        given(pagosClient.reembolsar(PAGO_ID, pedido.getId()))
                .willReturn(pago(EstadoPago.REEMBOLSADO, TOTAL));

        service.cancelarPedido(pedido.getId(), CLIENTE);

        assertThat(leerDeLaBase(pedido.getId()).getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(leerHistorialDeLaBase(pedido.getId()))
                .extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO, EstadoPedido.CANCELADO);
    }

    // --------------------------------------------------------- CU-02 alta parcial

    @Test
    @DisplayName("CU-02: un lote con una linea invalida no deja ninguna linea nueva en la base")
    void agregarLoteInvalidoNoDejaLineasAMedias() {
        Pedido pedido = darBorradorPersistido(1);
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, new BigDecimal("1200.00"), true)));
        // El tercero no existe en Catalogo: corta despues de haber agregado el segundo en memoria.
        UUID productoInexistente = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        given(catalogoClient.buscarProducto(productoInexistente)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.agregarItems(pedido.getId(), CLIENTE,
                new AgregarItemsPedidoRequest(List.of(
                        new LineaItem(OTRO_PRODUCTO, 3),
                        new LineaItem(productoInexistente, 1)))));

        assertThat(((ReglaNegocioException) error).getCodigo()).isEqualTo(CodigoError.VALIDATION_ERROR);
        Pedido enLaBase = leerConItemsDeLaBase(pedido.getId());
        // Ni la linea valida del lote: el rollback deja el pedido como estaba.
        assertThat(enLaBase.getItems())
                .extracting("productoId")
                .containsExactly(PRODUCTO);
        assertThat(enLaBase.getTotal()).isEqualByComparingTo(PRECIO);
        assertThat(leerHistorialDeLaBase(pedido.getId())).hasSize(1);
    }
}
