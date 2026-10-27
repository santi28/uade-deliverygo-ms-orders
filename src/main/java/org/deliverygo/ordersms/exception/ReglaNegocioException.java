package org.deliverygo.ordersms.exception;

/**
 * Violacion de una regla de negocio (RN-01..RN-06).
 *
 * <p>Lleva el {@link CodigoError} con el que se responde, para que el service decida el
 * caso y no el {@code GlobalExceptionHandler}.
 */
public class ReglaNegocioException extends RuntimeException {

    private final CodigoError codigo;

    public ReglaNegocioException(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoError getCodigo() {
        return codigo;
    }
}
