package org.deliverygo.ordersms.exception;

import java.util.UUID;

/**
 * ORDER_NOT_FOUND — El pedido no existe o pertenece a otro cliente (REP-03).
 *
 * <p>Los dos casos devuelven lo mismo a proposito: distinguirlos permitiria averiguar
 * que pedidos existen.
 */
public class PedidoNotFoundException extends RuntimeException {

    public PedidoNotFoundException(UUID pedidoId) {
        super("No existe un pedido " + pedidoId + " para el cliente indicado");
    }
}
