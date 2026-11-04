package org.deliverygo.ordersms.client.mock;

import java.util.UUID;

/**
 * Identificadores fijos que usan los mocks de INT-01..INT-04 mientras los modulos
 * reales no esten disponibles (Etapa 4).
 *
 * <p>Son deterministas a proposito: permiten reproducir desde Swagger tanto el camino
 * feliz como cada error de negocio.
 *
 * <p>Los ids de producto y comercio son numericos porque asi los emite Catalogo
 * (DM-03); los de cliente y direccion siguen siendo UUID porque son nuestros.
 */
public final class DatosMock {

    /** Usuario activo con una direccion registrada. */
    public static final UUID CLIENTE_ACTIVO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DIRECCION_CLIENTE_ACTIVO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    /** Usuario dado de baja: sirve para probar RN-01. */
    public static final UUID CLIENTE_INACTIVO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID DIRECCION_CLIENTE_INACTIVO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    public static final Long COMERCIO_PIZZERIA = 10L;
    public static final Long COMERCIO_KIOSCO = 20L;

    public static final Long PRODUCTO_PIZZA = 1L;
    public static final Long PRODUCTO_EMPANADA = 2L;
    /** Producto de otro comercio: sirve para probar RN-02 (MIXED_MERCHANT). */
    public static final Long PRODUCTO_GASEOSA = 3L;
    /** Producto sin stock: sirve para probar RN-05 (PRODUCT_UNAVAILABLE). */
    public static final Long PRODUCTO_HELADO = 4L;

    /** Medio de pago registrado en Pagos que usa el mock de INT-03. */
    public static final UUID MEDIO_PAGO = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private DatosMock() {
    }
}
