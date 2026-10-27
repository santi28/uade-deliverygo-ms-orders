package org.deliverygo.ordersms.exception;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error normalizados del modulo (tabla de errores del plan).
 *
 * <p>Cada codigo viaja tal cual en {@code ErrorResponse.code} y define el HTTP con el que
 * se responde, para que el mismo caso de negocio no termine con dos status distintos
 * segun quien lo lance.
 */
public enum CodigoError {

    /** Dato faltante o cantidad invalida (RN-01). */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /** Pedido inexistente o de otro cliente. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** Transicion de estado no permitida (RN-04). */
    ORDER_INVALID_STATE(HttpStatus.CONFLICT),

    /** Producto de otro comercio (RN-02). */
    MIXED_MERCHANT(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Producto inactivo o sin stock (RN-05). */
    PRODUCT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Precio distinto al confirmar (RN-03). */
    PRICE_CHANGED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Pagos rechazo la operacion (INT-03). */
    PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Catalogo, Usuarios o Pagos no responde (INT-01..04). */
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /** Fallo no contemplado; no figura en el plan, evita filtrar stacktraces. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    CodigoError(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
