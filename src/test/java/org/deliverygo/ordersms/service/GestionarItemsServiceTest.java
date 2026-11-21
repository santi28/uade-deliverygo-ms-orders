package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.dto.AgregarItemsPedidoRequest;
import org.deliverygo.ordersms.dto.AgregarItemsPedidoRequest.LineaItem;
import org.deliverygo.ordersms.dto.ModificarCantidadItemRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.deliverygo.ordersms.support.PedidosFixture.CLIENTE;
import static org.deliverygo.ordersms.support.PedidosFixture.OTRO_PRODUCTO;
import static org.deliverygo.ordersms.support.PedidosFixture.PAGO_ID;
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
 * SRV-02 — CU-02 gestionar items: alta (API-02), cambio de cantidad (API-02b) y baja
 * (API-02c).
 *
 * <p>Las tres operaciones comparten las reglas de edicion: solo borradores (RN-04), un
 * pedido ajeno responde igual que uno inexistente (REP-02) y el total se recalcula en
 * cada cambio. El prefijo del nombre de cada test dice de que verbo es el caso.
 *
 * <p>El precio nunca llega del request: lo pone Catalogo (INT-01) y lo congela
 * {@code ItemPedido} (RN-03).
 *
 * <p>Los casos de cada verbo no van en clases {@code @Nested}: surefire las descarta en
 * una corrida normal —el nombre {@code ...Test$Caso} no coincide con sus patrones de
 * inclusion— y los tests pasarian a no ejecutarse sin que nada avise.
 */
class GestionarItemsServiceTest extends PedidoServiceTestBase {

    private static final UUID CLIENTE_AJENO = UUID.fromString("0f0f0f0f-0f0f-0f0f-0f0f-0f0f0f0f0f0f");

    private static AgregarItemsPedidoRequest alta(LineaItem... lineas) {
        return new AgregarItemsPedidoRequest(List.of(lineas));
    }

    // ---------------------------------------------------------------- API-02 alta

    @Test
    @DisplayName("API-02: agrega las lineas con el precio de Catalogo y recalcula el total")
    void agregaLineasYRecalculaTotal() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(producto(OTRO_PRODUCTO, new BigDecimal("1200.00"), true)));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.agregarItems(PEDIDO_ID, CLIENTE,
                alta(new LineaItem(PRODUCTO, 2), new LineaItem(OTRO_PRODUCTO, 3)));

        assertThat(respuesta.items()).hasSize(2);
        // RN-03: el precio unitario es el que devolvio Catalogo, no uno del request.
        assertThat(respuesta.items())
                .extracting(PedidoResponse.ItemResponse::productoId, PedidoResponse.ItemResponse::cantidad,
                        PedidoResponse.ItemResponse::precioUnitario)
                .containsExactly(
                        tuple(PRODUCTO, 2, PRECIO),
                        tuple(OTRO_PRODUCTO, 3, new BigDecimal("1200.00")));
        // 2 x 8500 + 3 x 1200 = 20600
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("20600.00"));
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.BORRADOR);
        // RN-04: la edicion queda asentada como BORRADOR a BORRADOR.
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> {
                    assertThat(entrada.estadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.estadoNuevo()).isEqualTo(EstadoPedido.BORRADOR);
                    assertThat(entrada.motivo()).isEqualTo("CU-02: 2 linea(s) agregada(s)");
                });
    }

    @Test
    @DisplayName("API-02: un producto que ya esta en el pedido se rechaza con VALIDATION_ERROR")
    void agregarRechazaProductoYaPresente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borradorConItem(1)));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        // El POST no acumula cantidad: para eso esta el PUT (desvio registrado el 27/10).
        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("ya esta en el pedido");
    }

    @Test
    @DisplayName("API-02: un producto repetido dentro del mismo request se rechaza sin consultar Catalogo")
    void agregarRechazaProductoRepetidoEnElRequest() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borrador()));

        Throwable error = catchThrowable(() -> service.agregarItems(PEDIDO_ID, CLIENTE,
                alta(new LineaItem(PRODUCTO, 1), new LineaItem(PRODUCTO, 2))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("repetido");
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("API-02, RN-02: un producto de otro comercio corta con MIXED_MERCHANT")
    void agregarRechazaProductoDeOtroComercio() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(productoDeOtroComercio(OTRO_PRODUCTO)));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(OTRO_PRODUCTO, 1))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.MIXED_MERCHANT);
        assertThat(pedido.getItems()).isEmpty();
    }

    @Test
    @DisplayName("API-02, RN-05: un producto disponible pero INACTIVO en Catalogo corta con PRODUCT_UNAVAILABLE")
    void agregarRechazaProductoInactivo() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoInactivo(PRODUCTO)));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRODUCT_UNAVAILABLE);
        assertThat(pedido.getItems()).isEmpty();
    }

    @Test
    @DisplayName("API-02, RN-05: un producto sin stock corta con PRODUCT_UNAVAILABLE")
    void agregarRechazaProductoNoDisponible() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, PRECIO, false)));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRODUCT_UNAVAILABLE);
        assertThat(pedido.getItems()).isEmpty();
    }

    @Test
    @DisplayName("API-02: un producto que no existe en Catalogo se rechaza con VALIDATION_ERROR")
    void agregarRechazaProductoInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borrador()));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.empty());

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no existe en Catalogo");
    }

    @Test
    @DisplayName("API-02: un lote con una linea invalida corta sin agregar la otra")
    void agregarNoAgregaParcialmente() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.agregarItems(PEDIDO_ID, CLIENTE,
                alta(new LineaItem(PRODUCTO, 1), new LineaItem(OTRO_PRODUCTO, 1))));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        // En la base no queda ninguna de las dos porque la transaccion del CU se deshace;
        // eso se verifica con los tests de rollback, aca solo que la linea mala no entro.
        assertThat(pedido.getItems()).extracting("productoId").doesNotContain(OTRO_PRODUCTO);
    }

    @Test
    @DisplayName("API-02, INT-01: si Catalogo no responde no se agrega nada (503)")
    void agregarPropagaCatalogoCaido() {
        Pedido pedido = borrador();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willThrow(new DependenciaNoDisponibleException("Catalogo", new RuntimeException("timeout")));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(error).isInstanceOf(DependenciaNoDisponibleException.class);
        assertThat(pedido.getItems()).isEmpty();
    }

    @Test
    @DisplayName("API-02, RN-04: un pedido confirmado no se edita, y no se consulta Catalogo")
    void agregarRechazaPedidoNoEditable() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID))
                .willReturn(Optional.of(enEstado(EstadoPedido.CONFIRMADO, PAGO_ID)));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(OTRO_PRODUCTO, 1))));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        // El estado se valida antes de tocar el agregado: no queda a medio editar.
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("API-02, REP-02: un pedido de otro cliente responde ORDER_NOT_FOUND")
    void agregarRechazaPedidoAjeno() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borrador()));

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE_AJENO, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    @DisplayName("API-02: un pedido inexistente responde ORDER_NOT_FOUND")
    void agregarRechazaPedidoInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(
                () -> service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(PRODUCTO, 1))));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
    }

    // ------------------------------------------------ API-02b cambio de cantidad

    @Test
    @DisplayName("API-02b: reemplaza la cantidad en vez de sumarla y recalcula el total")
    void modificarReemplazaLaCantidad() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO)).willReturn(Optional.of(productoVendible(PRODUCTO)));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.modificarCantidadItem(PEDIDO_ID, CLIENTE, PRODUCTO,
                new ModificarCantidadItemRequest(5));

        assertThat(respuesta.items()).singleElement()
                .satisfies(item -> {
                    // 5, no 7: la cantidad enviada es la final.
                    assertThat(item.cantidad()).isEqualTo(5);
                    assertThat(item.subtotal()).isEqualByComparingTo(new BigDecimal("42500.00"));
                });
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("42500.00"));
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> assertThat(entrada.motivo())
                        .isEqualTo("CU-02: producto " + PRODUCTO + " con cantidad 5"));
    }

    @Test
    @DisplayName("API-02b, RN-03: mantiene el precio congelado aunque Catalogo ya cobre otro")
    void modificarMantieneElPrecioCongelado() {
        Pedido pedido = borradorConItem(1);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        // Catalogo aumento el producto: el borrador sigue con el precio de cuando se agrego.
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, new BigDecimal("9900.00"), true)));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.modificarCantidadItem(PEDIDO_ID, CLIENTE, PRODUCTO,
                new ModificarCantidadItemRequest(2));

        // El cambio de precio lo detecta CU-03 al confirmar (PRICE_CHANGED), no CU-02.
        assertThat(respuesta.items()).singleElement()
                .satisfies(item -> assertThat(item.precioUnitario()).isEqualByComparingTo(PRECIO));
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("17000.00"));
    }

    @Test
    @DisplayName("API-02b: una linea que no esta en el pedido se rechaza con VALIDATION_ERROR")
    void modificarRechazaLineaInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(borradorConItem(1)));

        Throwable error = catchThrowable(() -> service.modificarCantidadItem(PEDIDO_ID, CLIENTE, OTRO_PRODUCTO,
                new ModificarCantidadItemRequest(2)));

        // No es 404: ORDER_NOT_FOUND esta definido para el pedido, no para la linea.
        assertThat(codigoDe(error)).isEqualTo(CodigoError.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("no esta en el pedido");
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("API-02b, RN-05: una linea que quedo sin stock no puede crecer")
    void modificarRechazaLineaSinStock() {
        Pedido pedido = borradorConItem(1);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(PRODUCTO))
                .willReturn(Optional.of(producto(PRODUCTO, PRECIO, false)));

        Throwable error = catchThrowable(() -> service.modificarCantidadItem(PEDIDO_ID, CLIENTE, PRODUCTO,
                new ModificarCantidadItemRequest(10)));

        assertThat(codigoDe(error)).isEqualTo(CodigoError.PRODUCT_UNAVAILABLE);
        // La cantidad no se toco.
        assertThat(pedido.getItems().getFirst().getCantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("API-02b, RN-04: un pedido cancelado no se edita")
    void modificarRechazaPedidoNoEditable() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID))
                .willReturn(Optional.of(enEstado(EstadoPedido.CANCELADO, null)));

        Throwable error = catchThrowable(() -> service.modificarCantidadItem(PEDIDO_ID, CLIENTE, PRODUCTO,
                new ModificarCantidadItemRequest(2)));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("API-02b: un pedido inexistente responde ORDER_NOT_FOUND")
    void modificarRechazaPedidoInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.modificarCantidadItem(PEDIDO_ID, CLIENTE, PRODUCTO,
                new ModificarCantidadItemRequest(2)));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
    }

    // ------------------------------------------------------- API-02c baja de linea

    @Test
    @DisplayName("API-02c: saca la linea, recalcula el total y lo asienta en el historial")
    void quitarSacaLaLinea() {
        Pedido pedido = borradorConItem(2);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO);

        assertThat(respuesta.items()).isEmpty();
        assertThat(respuesta.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> assertThat(entrada.motivo())
                        .isEqualTo("CU-02: producto " + PRODUCTO + " quitado"));
    }

    @Test
    @DisplayName("API-02c: es idempotente, una linea que no esta deja el pedido igual y sin historial")
    void quitarEsIdempotente() {
        Pedido pedido = borradorConItem(2);
        int historialAntes = pedido.getHistorial().size();
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.quitarItem(PEDIDO_ID, CLIENTE, OTRO_PRODUCTO);

        // Reintentar el DELETE no falla ni ensucia el historial.
        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("17000.00"));
        assertThat(pedido.getHistorial()).hasSize(historialAntes);
    }

    @Test
    @DisplayName("API-02c: no consulta Catalogo, un producto dado de baja igual se saca del borrador")
    void quitarNoConsultaCatalogo() {
        Pedido pedido = borradorConItem(1);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        dadoHistorialPersistido(pedido);

        service.quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO);

        verifyNoInteractions(catalogoClient);
    }

    @Test
    @DisplayName("API-02c, RN-04: un pedido confirmado no se edita")
    void quitarRechazaPedidoNoEditable() {
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));

        Throwable error = catchThrowable(() -> service.quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO));

        assertThat(error).isInstanceOf(EstadoInvalidoException.class);
        assertThat(pedido.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("API-02c: un pedido inexistente responde ORDER_NOT_FOUND")
    void quitarRechazaPedidoInexistente() {
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.empty());

        Throwable error = catchThrowable(() -> service.quitarItem(PEDIDO_ID, CLIENTE, PRODUCTO));

        assertThat(error).isInstanceOf(PedidoNotFoundException.class);
    }

    @Test
    @DisplayName("desvio 17/11: con el interruptor prendido CU-02 edita un pedido CONFIRMADO")
    void conInterruptorPrendidoEditaUnConfirmado() {
        dadoElInterruptorDeRn04Prendido();
        Pedido pedido = enEstado(EstadoPedido.CONFIRMADO, PAGO_ID);
        given(pedidoRepository.findByIdConItems(PEDIDO_ID)).willReturn(Optional.of(pedido));
        given(catalogoClient.buscarProducto(OTRO_PRODUCTO))
                .willReturn(Optional.of(productoVendible(OTRO_PRODUCTO)));
        dadoHistorialPersistido(pedido);

        PedidoResponse respuesta = service.agregarItems(PEDIDO_ID, CLIENTE, alta(new LineaItem(OTRO_PRODUCTO, 1)));

        // El pedido sigue CONFIRMADO: la edicion no lo vuelve a BORRADOR, solo deja la
        // entrada en el historial marcando que se salteo RN-04.
        assertThat(respuesta.items()).hasSize(2);
        assertThat(respuesta.historial()).last()
                .satisfies(entrada -> assertThat(entrada.motivo()).contains("RN-04 salteada"));
    }
}
