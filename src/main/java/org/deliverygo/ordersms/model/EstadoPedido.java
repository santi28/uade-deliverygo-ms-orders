package org.deliverygo.ordersms.model;

/**
 * DM-04 — Estados posibles de un {@link Pedido}.
 *
 * <p>Se persiste siempre como texto ({@code @Enumerated(EnumType.STRING)}) para que el
 * orden de declaracion no quede acoplado a la base de datos.
 *
 * <p>Las transiciones validas entre estados no se definen aca: son responsabilidad de
 * {@code MaquinaEstados} (RN-04).
 */
public enum EstadoPedido {
    BORRADOR,
    CONFIRMADO,
    EN_PREPARACION,
    EN_CAMINO,
    ENTREGADO,
    CANCELADO
}
