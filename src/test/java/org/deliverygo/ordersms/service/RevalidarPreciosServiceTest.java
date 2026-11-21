package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.dto.RevalidacionPreciosResponse;
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
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.OTRO_PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.PEDIDO_ID;
import static org.deliverygo.ordersms.support.PedidosFixture.PRECIO;
import static org.deliverygo.ordersms.support.PedidosFixture.PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.borrador;
import static org.deliverygo.ordersms.support.PedidosFixture.borradorConItem;
import static org.deliverygo.ordersms.support.PedidosFixture.enEstado;
import static org.deliverygo.ordersms.support.PedidosFixture.producto;
import static org.deliverygo.ordersms.support.PedidosFixture.productoInactivo;
import static org.deliverygo.ordersms.support.PedidosFixture.productoDeOtroComercio;
import static org.deliverygo.ordersms.support.PedidosFixture.productoVendible;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * SRV-07 — CU-07 revalidar precios: camino feliz y flujos alternativos.
 *
 * <p>Lo central es que solo informa: ninguna diferencia corta el CU ni modifica el
 * pedido, a diferencia de CU-03, que con la misma regla corta en la primera.
 */
class RevalidarPreciosServiceTest extends PedidoServiceTestBase {

    private static final BigDecimal PRECIO_EMPANADA = new BigDecimal("1200.00");

    private Pedido dadoPedido(Pedido pedido) {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        return pedido;
    }

    /** Borrador con 2 pizzas y 3 empanadas: total 20600.00. */
    private Pedido borradorConDosLineas() {
        Pedido pedido = borradorConItem(2);
        pedido.agregarItem(OTRO_PRODUCTO, "Empanada de carne", 3, PRECIO_EMPANADA);
        return pedido;
    }

    @Test
    @DisplayName("sin cambios en Catalogo el pedido es valido y los dos totales coinciden")
    void sinCambiosEsValido() {
        Pedido pedido = dadoPedido(borradorConDosLineas());
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, PRECIO_EMPANADA, true)));

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.valido()).isTrue();
        assertThat(respuesta.diferencias()).isEmpty();
        assertThat(respuesta.totalGuardado()).isEqualByComparingTo("20600.00");
        assertThat(respuesta.totalVigente()).isEqualByComparingTo(pedido.getTotal());
    }

    @Test
    @DisplayName("RN-03: un precio distinto se informa con el total nuevo y no toca el pedido")
    void precioDistintoSeInforma() {
        Pedido pedido = dadoPedido(borradorConDosLineas());
        BigDecimal precioNuevo = new BigDecimal("9200.00");
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(producto(PRODUCTO, precioNuevo, true)));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, PRECIO_EMPANADA, true)));
        int entradas = pedido.getHistorial().size();

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.valido()).isFalse();
        assertThat(respuesta.totalVigente()).isEqualByComparingTo("22000.00");
        assertThat(respuesta.diferencias()).singleElement().satisfies(diferencia -> {
            assertThat(diferencia.productoId()).isEqualTo(PRODUCTO);
            assertThat(diferencia.precioGuardado()).isEqualByComparingTo(PRECIO);
            assertThat(diferencia.precioVigente()).isEqualByComparingTo(precioNuevo);
            assertThat(diferencia.disponible()).isTrue();
            assertThat(diferencia.motivo()).isEqualTo("PRICE_CHANGED");
        });
        // Sin sobrescritura silenciosa: el precio congelado y el historial quedan igual.
        assertThat(pedido.getTotal()).isEqualByComparingTo("20600.00");
        assertThat(pedido.getHistorial()).hasSize(entradas);
    }

    @Test
    @DisplayName("RN-05: un producto sin stock se informa y queda fuera del total vigente")
    void sinStockQuedaFueraDelTotal() {
        dadoPedido(borradorConDosLineas());
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(producto(PRODUCTO, PRECIO, false)));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, PRECIO_EMPANADA, true)));

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.valido()).isFalse();
        assertThat(respuesta.totalVigente()).isEqualByComparingTo("3600.00");
        assertThat(respuesta.diferencias()).singleElement().satisfies(diferencia -> {
            assertThat(diferencia.disponible()).isFalse();
            assertThat(diferencia.motivo()).isEqualTo("PRODUCT_UNAVAILABLE");
        });
    }

    @Test
    @DisplayName("RN-05: un producto INACTIVO en Catalogo se informa como no disponible")
    void inactivoNoDisponible() {
        dadoPedido(borradorConItem(1));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoInactivo(PRODUCTO)));

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.diferencias()).singleElement().satisfies(diferencia -> {
            assertThat(diferencia.disponible()).isFalse();
            assertThat(diferencia.motivo()).isEqualTo("PRODUCT_UNAVAILABLE");
        });
    }

    @Test
    @DisplayName("un producto que ya no esta en Catalogo se informa sin precio vigente")
    void productoAusenteSinPrecio() {
        dadoPedido(borradorConItem(1));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.empty());

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.totalVigente()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.diferencias()).singleElement().satisfies(diferencia -> {
            assertThat(diferencia.precioVigente()).isNull();
            assertThat(diferencia.disponible()).isFalse();
            assertThat(diferencia.motivo()).isEqualTo("PRODUCT_UNAVAILABLE");
        });
    }

    @Test
    @DisplayName("RN-02: un producto que paso a otro comercio se informa como no disponible")
    void otroComercioNoDisponible() {
        dadoPedido(borradorConItem(1));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoDeOtroComercio(PRODUCTO)));

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.diferencias()).singleElement().satisfies(diferencia -> {
            assertThat(diferencia.disponible()).isFalse();
            assertThat(diferencia.motivo()).isEqualTo("MIXED_MERCHANT");
        });
    }

    @Test
    @DisplayName("varias diferencias se informan todas, no solo la primera")
    void informaTodasLasDiferencias() {
        dadoPedido(borradorConDosLineas());
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.empty());
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, new BigDecimal("1500.00"), true)));

        RevalidacionPreciosResponse respuesta = service.revalidarPrecios(PEDIDO_ID, CLIENTE);

        assertThat(respuesta.diferencias()).extracting(RevalidacionPreciosResponse.DiferenciaLinea::motivo)
                .containsExactly("PRODUCT_UNAVAILABLE", "PRICE_CHANGED");
    }

    @Test
    @DisplayName("INT-01: si Catalogo no responde sale 503, nunca un precio supuesto")
    void catalogoCaido() {
        dadoPedido(borradorConItem(1));
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willThrow(new DependenciaNoDisponibleException("Catalogo", new RestClientException("timeout")));

        Throwable error = catchThrowable(() -> service.revalidarPrecios(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
    }

    @Test
    @DisplayName("un pedido sin lineas responde VALIDATION_ERROR sin consultar Catalogo")
    void pedidoSinLineas() {
        dadoPedido(borrador());

        Throwable error = catchThrowable(() -> service.revalidarPrecios(PEDIDO_ID, CLIENTE));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("RN-04: un pedido ya confirmado no se revalida")
    void confirmadoNoSeRevalida() {
        dadoPedido(enEstado(EstadoPedido.CONFIRMADO, null));

        Throwable error = catchThrowable(() -> service.revalidarPrecios(PEDIDO_ID, CLIENTE));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("REP-02: un pedido de otro cliente responde ORDER_NOT_FOUND")
    void pedidoDeOtroCliente() {
        dadoPedido(borradorConItem(1));

        Throwable error = catchThrowable(() -> service.revalidarPrecios(PEDIDO_ID, UUID.randomUUID()));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
        verifyNoInteractions(catalogoClient);
    }
}
