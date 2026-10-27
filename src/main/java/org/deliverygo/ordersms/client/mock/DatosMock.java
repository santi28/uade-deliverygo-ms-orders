package org.deliverygo.ordersms.client.mock;

import java.util.UUID;

/**
 * Identificadores fijos que usan los mocks de INT-01..INT-04 mientras los modulos reales
 * no esten disponibles (Etapa 4).
 *
 * <p>Son deterministas a proposito: permiten reproducir desde Swagger tanto el camino
 * feliz como cada error de negocio.
 */
public final class DatosMock {

    /** Usuario activo con una direccion registrada. */
    public static final UUID CLIENTE_ACTIVO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DIRECCION_CLIENTE_ACTIVO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    /** Usuario dado de baja: sirve para probar RN-01. */
    public static final UUID CLIENTE_INACTIVO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID DIRECCION_CLIENTE_INACTIVO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    public static final UUID COMERCIO_PIZZERIA = UUID.fromString("99999999-9999-9999-9999-999999999999");
    public static final UUID COMERCIO_KIOSCO = UUID.fromString("88888888-8888-8888-8888-888888888888");

    public static final UUID PRODUCTO_PIZZA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID PRODUCTO_EMPANADA = UUID.fromString("44444444-4444-4444-4444-444444444444");
    /** Producto de otro comercio: sirve para probar RN-02 (MIXED_MERCHANT). */
    public static final UUID PRODUCTO_GASEOSA = UUID.fromString("55555555-5555-5555-5555-555555555555");
    /** Producto sin stock: sirve para probar RN-05 (PRODUCT_UNAVAILABLE). */
    public static final UUID PRODUCTO_HELADO = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private DatosMock() {
    }
}
