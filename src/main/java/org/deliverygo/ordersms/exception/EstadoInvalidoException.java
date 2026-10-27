package org.deliverygo.ordersms.exception;

import org.deliverygo.ordersms.model.EstadoPedido;

/**
 * ORDER_INVALID_STATE — Transicion de estado no permitida (RN-04).
 *
 * <p>La lanza {@code MaquinaEstados}; cuando se lanza no se persiste nada.
 */
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(EstadoPedido desde, EstadoPedido hacia) {
        super("Transicion no permitida de " + desde + " a " + hacia);
    }
}
