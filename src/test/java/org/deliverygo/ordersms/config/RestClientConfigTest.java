package org.deliverygo.ordersms.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.deliverygo.ordersms.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * INT-01 a INT-04 — Timeout y propagacion de cabeceras del {@link RestClient} comun.
 *
 * <p>Se prueba contra un servidor HTTP de verdad (el del JDK) porque es lo unico que
 * demuestra que el timeout esta aplicado: un mock no deja de responder. El caso que
 * importa es el que el plan pide evitar, un modulo que no contesta colgando una peticion
 * nuestra.
 *
 * <p>Los timeouts del test son mas cortos que los de produccion (2s y 3s) para que la
 * suite no tarde: lo que se verifica es que el valor configurado se aplique, no cual es.
 * Que los valores reales se lean bien de {@code application.properties} lo cubre el
 * arranque del contexto en {@code OrdersMsApplicationTests}, porque el bean es eager.
 */
class RestClientConfigTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    /** Lo que tarda el servidor lento: bastante mas que el read-timeout. */
    private static final Duration DEMORA_DEL_SERVIDOR = Duration.ofSeconds(3);

    private final RestClientConfig config = new RestClientConfig();

    private HttpServer servidor;
    private RestClient cliente;
    private final AtomicReference<HttpHeaders> cabecerasRecibidas = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        // Puerto 0: el sistema asigna uno libre.
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/rapido", intercambio -> {
            cabecerasRecibidas.set(copiarCabeceras(intercambio));
            responder(intercambio, "{}");
        });
        servidor.createContext("/lento", intercambio -> {
            try {
                Thread.sleep(DEMORA_DEL_SERVIDOR.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(intercambio, "{}");
        });
        servidor.start();

        cliente = config.restClientBuilder(CONNECT_TIMEOUT, READ_TIMEOUT)
                .baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort())
                .build();
    }

    @AfterEach
    void bajarServidor() {
        servidor.stop(0);
        RequestContextHolder.resetRequestAttributes();
    }

    private static HttpHeaders copiarCabeceras(HttpExchange intercambio) {
        HttpHeaders cabeceras = new HttpHeaders();
        intercambio.getRequestHeaders().forEach(cabeceras::addAll);
        return cabeceras;
    }

    private static void responder(HttpExchange intercambio, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        intercambio.sendResponseHeaders(200, bytes.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private String llamarA(String ruta) {
        return cliente.get().uri(ruta).retrieve().body(String.class);
    }

    @Test
    @DisplayName("un modulo que no responde corta al vencer el read-timeout, no espera su respuesta")
    void cortaAlVencerElReadTimeout() {
        long inicio = System.nanoTime();

        Throwable error = catchThrowable(() -> llamarA("/lento"));

        Duration tardanza = Duration.ofNanos(System.nanoTime() - inicio);
        // Spring envuelve el timeout de transporte en ResourceAccessException; traducirlo a
        // DependenciaNoDisponibleException (503) es tarea de cada cliente HTTP (Etapa 4).
        assertThat(error).isInstanceOf(ResourceAccessException.class);
        // Lo que importa: cortamos nosotros, no el otro modulo cuando se le ocurre contestar.
        assertThat(tardanza).isLessThan(DEMORA_DEL_SERVIDOR);
    }

    @Test
    @DisplayName("una respuesta dentro del timeout se procesa normalmente")
    void respondeDentroDelTimeout() {
        assertThat(llamarA("/rapido")).isEqualTo("{}");
    }

    @Test
    @DisplayName("propaga el Authorization y el X-Correlation-Id de la peticion en curso")
    void propagaIdentidadYCorrelacion() {
        MockHttpServletRequest entrante = new MockHttpServletRequest();
        entrante.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-de-usuarios");
        entrante.addHeader(GlobalExceptionHandler.HEADER_CORRELACION, "correlacion-del-caso");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrante));

        llamarA("/rapido");

        HttpHeaders recibidas = cabecerasRecibidas.get();
        // El otro modulo autoriza al mismo usuario que nos llamo (RN-01).
        assertThat(recibidas.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-de-usuarios");
        // El caso se puede rastrear en los logs de los dos modulos.
        assertThat(recibidas.getFirst(GlobalExceptionHandler.HEADER_CORRELACION))
                .isEqualTo("correlacion-del-caso");
    }

    @Test
    @DisplayName("genera un X-Correlation-Id cuando la peticion entrante no trae ninguno")
    void generaCorrelacionSiLaPeticionNoTraeUna() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        llamarA("/rapido");

        HttpHeaders recibidas = cabecerasRecibidas.get();
        assertThat(recibidas.getFirst(GlobalExceptionHandler.HEADER_CORRELACION)).isNotBlank();
        assertThat(recibidas.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    @DisplayName("fuera de una peticion HTTP la llamada sale igual, con correlacion generada")
    void funcionaFueraDeUnaPeticionHttp() {
        // Sin RequestContextHolder: una tarea de fondo o un test no tienen nada que propagar.
        llamarA("/rapido");

        assertThat(cabecerasRecibidas.get().getFirst(GlobalExceptionHandler.HEADER_CORRELACION)).isNotBlank();
    }
}
