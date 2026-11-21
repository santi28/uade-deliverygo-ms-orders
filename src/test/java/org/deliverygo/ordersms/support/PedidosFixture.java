package org.deliverygo.ordersms.support;

import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.UsuarioDto;
import org.deliverygo.ordersms.client.mock.DatosMock;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pedidos armados a mano para los tests que no pasan por la base.
 *
 * <p>Fuera de {@code @DataJpaTest} nadie le asigna el id al agregado, porque lo genera
 * JPA al persistir ({@code GenerationType.UUID}): el service lo usa para loguear y para
 * {@link org.deliverygo.ordersms.dto.PedidoResponse}, asi que aca se asigna por
 * reflexion. Los ids de referencia salen de {@link DatosMock} para que un caso se lea
 * igual en los tests de Service, los de Controller y la coleccion Postman.
 */
public final class PedidosFixture {

    public static final UUID PEDIDO_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final UUID CLIENTE = DatosMock.CLIENTE_ACTIVO;
    public static final UUID DIRECCION = DatosMock.DIRECCION_CLIENTE_ACTIVO;
    public static final UUID OTRA_DIRECCION = DatosMock.DIRECCION_CLIENTE_INACTIVO;
    public static final Long COMERCIO = DatosMock.COMERCIO_PIZZERIA;
    public static final Long OTRO_COMERCIO = DatosMock.COMERCIO_KIOSCO;
    public static final Long PRODUCTO = DatosMock.PRODUCTO_PIZZA;
    public static final Long OTRO_PRODUCTO = DatosMock.PRODUCTO_EMPANADA;
    public static final BigDecimal PRECIO = new BigDecimal("8500.00");
    /** Pago que INT-03 devuelve al cobrar y que INT-04 reembolsa. */
    public static final UUID PAGO_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    /** Reembolso que INT-04 devuelve en CU-04. */
    public static final UUID REEMBOLSO_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    /** Estado de publicacion con el que Catalogo marca un producto vigente. */
    public static final String ESTADO_ACTIVO = "ACTIVO";

    private PedidosFixture() {
    }

    /** Borrador recien creado, sin lineas, con el nacimiento asentado en el historial. */
    public static Pedido borrador() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.registrarTransicion(EstadoPedido.BORRADOR, "Pedido creado como borrador");
        return conId(pedido, PEDIDO_ID);
    }

    /** Borrador con una linea de {@link #PRODUCTO}, que es lo que CU-03 necesita para cobrar. */
    public static Pedido borradorConItem(int cantidad) {
        Pedido pedido = borrador();
        pedido.agregarItem(PRODUCTO, "Pizza muzzarella", cantidad, PRECIO);
        return pedido;
    }

    /**
     * Pedido en un estado posterior a BORRADOR, para los casos de RN-04. Un pedido
     * CONFIRMADO queda con pago asociado, como lo deja CU-03.
     */
    public static Pedido enEstado(EstadoPedido estado, UUID pagoId) {
        Pedido pedido = borradorConItem(1);
        if (estado != EstadoPedido.BORRADOR) {
            pedido.registrarTransicion(estado, "Fixture: pedido en " + estado);
        }
        if (pagoId != null) {
            pedido.asociarPago(pagoId);
        }
        return pedido;
    }

    /**
     * Asigna el id que normalmente pone JPA al persistir, para emular lo que devuelve
     * {@code save()} en los tests con repositorios mockeados.
     */
    public static Pedido conId(Pedido pedido, UUID id) {
        ReflectionTestUtils.setField(pedido, "id", id);
        return pedido;
    }

    /** Producto de Catalogo (INT-01) del comercio del pedido. */
    public static ProductoDto producto(Long id, BigDecimal precio, boolean disponible) {
        return new ProductoDto(id, COMERCIO, "Pizza muzzarella", precio, ESTADO_ACTIVO, disponible);
    }

    /** RN-05 — Producto que Catalogo informa disponible pero despublicado: no se vende. */
    public static ProductoDto productoInactivo(Long id) {
        return new ProductoDto(id, COMERCIO, "Pizza muzzarella", PRECIO, "INACTIVO", true);
    }

    /** Producto vendible: del comercio del pedido, disponible y al precio congelado. */
    public static ProductoDto productoVendible(Long id) {
        return producto(id, PRECIO, true);
    }

    /** RN-02 — Producto que Catalogo ubica en otro comercio (MIXED_MERCHANT). */
    public static ProductoDto productoDeOtroComercio(Long id) {
        return new ProductoDto(id, OTRO_COMERCIO, "Gaseosa", PRECIO, ESTADO_ACTIVO, true);
    }

    /** RN-01, RN-06 — Usuario activo con {@link #DIRECCION} registrada (INT-02). */
    public static UsuarioDto usuarioActivo() {
        return new UsuarioDto(CLIENTE, "Cliente activo", true, List.of(DIRECCION));
    }

    /** RN-01 — Usuario dado de baja: no puede crear ni confirmar pedidos. */
    public static UsuarioDto usuarioInactivo() {
        return new UsuarioDto(CLIENTE, "Cliente dado de baja", false, List.of(DIRECCION));
    }

    /** RN-06 — Usuario activo al que la direccion del pedido ya no le pertenece. */
    public static UsuarioDto usuarioSinLaDireccion() {
        return new UsuarioDto(CLIENTE, "Cliente activo", true, List.of(OTRA_DIRECCION));
    }

    /** INT-03 — Respuesta de Pagos al iniciar o consultar el pago. */
    public static PagoDto pago(EstadoPago estado, BigDecimal importe) {
        return new PagoDto(PAGO_ID, PEDIDO_ID, estado, importe, Pedido.MONEDA_POR_DEFECTO);
    }

    /** INT-04 — Reembolso concedido por Pagos; es lo que habilita CANCELADO en CU-04. */
    public static ReembolsoDto reembolsoAprobado(BigDecimal importe) {
        return new ReembolsoDto(REEMBOLSO_ID, PAGO_ID, importe, "APROBADO", BigDecimal.ZERO);
    }

    /** INT-04 — Reembolso que Pagos no concede; el pedido sigue CONFIRMADO. */
    public static ReembolsoDto reembolsoRechazado(BigDecimal importe) {
        return new ReembolsoDto(REEMBOLSO_ID, PAGO_ID, importe, "RECHAZADO", importe);
    }
}
