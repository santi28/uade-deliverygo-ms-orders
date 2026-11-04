package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * INT-01 a INT-04 — Base comun de los clientes HTTP de Catalogo, Usuarios y Pagos.
 *
 * <p>No es una clase del diseño de la planilla: es el lugar donde vive la traduccion de
 * un fallo de transporte a {@link DependenciaNoDisponibleException}, que la Etapa 3 dejo
 * pendiente porque el mensaje tiene que nombrar al modulo que no respondio y eso solo lo
 * sabe cada cliente. Estar aca evita repetir el mismo {@code try/catch} tres veces y que
 * una integracion quede devolviendo una excepcion cruda de Spring al Service.
 *
 * <p>Criterio de traduccion, igual para los cuatro INT:
 *
 * <ul>
 *   <li>404 en una busqueda: el recurso no existe. Devuelve vacio, no es una caida.
 *   <li>Timeout, conexion rechazada, 5xx, otro 4xx o un cuerpo que no se puede
 *       deserializar: 503 DEPENDENCY_UNAVAILABLE. Un 400 o un 401 del otro modulo es un
 *       incumplimiento de contrato, no un dato: tampoco se continua.
 *   <li>200 con cuerpo vacio donde se esperaba un objeto: 503. Nunca se sigue con un
 *       dato supuesto (RN-03).
 * </ul>
 */
abstract class ClienteHttpDeModulo {

    /** Nombre del modulo tal como aparece en el mensaje de error del 503. */
    private final String modulo;

    /** Cliente ya apuntado a la URL base del modulo. */
    protected final RestClient rest;

    /**
     * @param modulo  nombre del modulo para el mensaje del 503
     * @param baseUrl URL base del modulo, de {@code deliverygo.integraciones.*.base-url}
     * @param builder builder comun de {@code RestClientConfig} (timeouts + cabeceras)
     */
    protected ClienteHttpDeModulo(String modulo, String baseUrl, RestClient.Builder builder) {
        this.modulo = modulo;
        // clone(): el builder es un bean compartido por los tres clientes y baseUrl() lo
        // mutaria. Sin esto, el ultimo cliente construido le cambiaria la URL a los otros.
        this.rest = builder.clone().baseUrl(baseUrl).build();
    }

    /**
     * Busqueda de un recurso que puede no existir: el 404 es una respuesta valida.
     *
     * @return el cuerpo, o vacio si el modulo respondio 404
     * @throws DependenciaNoDisponibleException si el modulo no respondio o respondio algo
     *                                          que no se puede interpretar
     */
    protected <T> Optional<T> buscar(Supplier<T> llamada) {
        try {
            return Optional.ofNullable(llamada.get());
        } catch (HttpClientErrorException.NotFound ausente) {
            return Optional.empty();
        } catch (RestClientException fallo) {
            throw new DependenciaNoDisponibleException(modulo, fallo);
        }
    }

    /**
     * Llamada cuya respuesta es obligatoria: cualquier cosa que no sea un cuerpo valido
     * corta el CU.
     *
     * @throws DependenciaNoDisponibleException si el modulo no respondio, respondio un
     *                                          error o respondio sin cuerpo
     */
    protected <T> T invocar(Supplier<T> llamada) {
        T respuesta;
        try {
            respuesta = llamada.get();
        } catch (RestClientException fallo) {
            throw new DependenciaNoDisponibleException(modulo, fallo);
        }

        if (respuesta == null) {
            throw new DependenciaNoDisponibleException(modulo,
                    new RestClientException("Respondio 2xx sin cuerpo"));
        }
        return respuesta;
    }
}
