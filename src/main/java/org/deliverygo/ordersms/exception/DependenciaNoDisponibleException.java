package org.deliverygo.ordersms.exception;

/**
 * DEPENDENCY_UNAVAILABLE — Catalogo, Usuarios o Pagos no respondio (INT-01..INT-04).
 *
 * <p>Nunca se continua con un dato supuesto: el CU corta y responde 503.
 */
public class DependenciaNoDisponibleException extends RuntimeException {

    public DependenciaNoDisponibleException(String modulo, Throwable causa) {
        super("El modulo " + modulo + " no esta disponible", causa);
    }
}
