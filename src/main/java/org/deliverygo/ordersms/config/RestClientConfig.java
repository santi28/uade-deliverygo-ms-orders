package org.deliverygo.ordersms.config;

import org.deliverygo.ordersms.exception.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-01 a INT-04 — {@link RestClient} comun de las llamadas a Catalogo, Usuarios y
 * Pagos.
 *
 * <p>Centraliza las dos cosas que el plan pide para todas las integraciones: timeout de
 * 2 a 3 segundos y propagacion de {@code Authorization} y {@code X-Correlation-Id}. Que
 * esten aca y no en cada cliente evita que una integracion quede sin timeout y cuelgue
 * una peticion nuestra esperando a un modulo caido.
 *
 * <p>Los valores se pueden ajustar por entorno con {@code deliverygo.integraciones.*}:
 *
 * <ul>
 *   <li>{@code connect-timeout} (2s por defecto): lo que se espera a que el otro modulo
 *       acepte la conexion.
 *   <li>{@code read-timeout} (3s por defecto): lo que se espera su respuesta. Es el techo
 *       de lo que puede tardar un CU por culpa de una dependencia.
 * </ul>
 *
 * <p>Este bean reemplaza al {@code RestClient.Builder} que autoconfigura Spring Boot, que
 * viene sin timeouts: sin esto una llamada saliente espera indefinidamente.
 *
 * <p>Traducir el fallo a {@code DependenciaNoDisponibleException} (503) es tarea de cada
 * cliente HTTP, porque el mensaje nombra al modulo que no respondio. Los clientes reales
 * son de la Etapa 4; hasta entonces corren los mocks ({@code @Profile("!integracion")}) y
 * este builder queda listo para cuando se enchufen.
 */
@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    /**
     * Builder compartido por los clientes de INT-01 a INT-04.
     *
     * @param connectTimeout espera maxima para establecer la conexion
     * @param readTimeout    espera maxima de la respuesta
     */
    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${deliverygo.integraciones.connect-timeout:2s}") Duration connectTimeout,
            @Value("${deliverygo.integraciones.read-timeout:3s}") Duration readTimeout) {
        log.info("Clientes de integracion con connect-timeout={} y read-timeout={}", connectTimeout, readTimeout);

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(readTimeout));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(propagarIdentidadYCorrelacion());
    }

    /**
     * Propaga a la llamada saliente el {@code Authorization} y el
     * {@code X-Correlation-Id} de la peticion que estamos atendiendo.
     *
     * <p>El token se reenvia tal cual: el otro modulo autoriza al mismo usuario que nos
     * llamo (RN-01). Si la peticion entrante no trae correlation id se genera uno, para
     * que el caso se pueda rastrear igual en los logs de los dos modulos.
     *
     * <p>Fuera de una peticion HTTP —una tarea de fondo, un test— no hay nada que
     * propagar y la llamada sale con el correlation id generado.
     */
    private ClientHttpRequestInterceptor propagarIdentidadYCorrelacion() {
        return (request, body, execution) -> {
            HttpHeaders salientes = request.getHeaders();

            peticionEnCurso().ifPresent(entrante -> {
                String autorizacion = entrante.getHeader(HttpHeaders.AUTHORIZATION);
                if (autorizacion != null && salientes.getFirst(HttpHeaders.AUTHORIZATION) == null) {
                    salientes.set(HttpHeaders.AUTHORIZATION, autorizacion);
                }

                String correlacion = entrante.getHeader(GlobalExceptionHandler.HEADER_CORRELACION);
                if (correlacion != null && !correlacion.isBlank()) {
                    salientes.set(GlobalExceptionHandler.HEADER_CORRELACION, correlacion);
                }
            });

            if (salientes.getFirst(GlobalExceptionHandler.HEADER_CORRELACION) == null) {
                salientes.set(GlobalExceptionHandler.HEADER_CORRELACION, UUID.randomUUID().toString());
            }

            return execution.execute(request, body);
        };
    }

    /** Peticion HTTP que se esta atendiendo, si la llamada sale desde una. */
    private Optional<HttpServletRequest> peticionEnCurso() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            return Optional.of(servlet.getRequest());
        }
        return Optional.empty();
    }
}
