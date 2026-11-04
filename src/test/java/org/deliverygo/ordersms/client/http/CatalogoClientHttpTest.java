package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.deliverygo.ordersms.exception.DependenciaNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * INT-01 — Cliente HTTP de Catalogo contra un servidor simulado.
 *
 * <p>Fija el contrato del OpenAPI del ecosistema v1.4.0: la ruta
 * {@code /api/v1/productos/{id}} con id numerico y el {@code ProductoResponseDTO} que
 * devuelve. Si Eq. 2 cambia alguna de las dos cosas, se ve aca antes que en la demo.
 *
 * <p>Un servidor simulado alcanza porque lo que se verifica es el contrato, no la red:
 * el timeout de verdad ya esta probado en {@code RestClientConfigTest}.
 */
class CatalogoClientHttpTest {

    private static final String BASE_URL = "http://catalogo.test";
    private static final Long PRODUCTO = 1L;
    private static final Long COMERCIO = 10L;
    private static final String URL_PRODUCTO = BASE_URL + "/api/v1/productos/" + PRODUCTO;

    private MockRestServiceServer catalogo;
    private CatalogoClientHttp cliente;

    @BeforeEach
    void prepararCliente() {
        RestClient.Builder builder = RestClient.builder();
        catalogo = MockRestServiceServer.bindTo(builder).build();
        cliente = new CatalogoClientHttp(builder, BASE_URL);
    }

    @Test
    @DisplayName("INT-01: GET al producto y mapeo del ProductoResponseDTO")
    void devuelveElProductoDelContratoPublicado() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 1,
                          "comercioId": 10,
                          "nombre": "Pizza muzzarella",
                          "precio": 8500.00,
                          "estado": "ACTIVO",
                          "disponible": true,
                          "categorias": [{"id": 5, "nombre": "Pizzas"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<ProductoDto> producto = cliente.buscarProducto(PRODUCTO);

        assertThat(producto).isPresent().get().satisfies(p -> {
            assertThat(p.id()).isEqualTo(PRODUCTO);
            assertThat(p.comercioId()).isEqualTo(COMERCIO);
            assertThat(p.nombre()).isEqualTo("Pizza muzzarella");
            assertThat(p.precio()).isEqualByComparingTo(new BigDecimal("8500.00"));
            // RN-05: publicado y habilitado. Las categorias que tambien manda Catalogo
            // se descartan sin romper el mapeo.
            assertThat(p.vendible()).isTrue();
        });
        catalogo.verify();
    }

    @Test
    @DisplayName("RN-05: un producto no disponible no es vendible")
    void noEsVendibleSiCatalogoLoMarcaNoDisponible() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withSuccess("""
                        {"id": 1, "comercioId": 10, "nombre": "Helado 1 kg",
                         "precio": 6000.00, "estado": "ACTIVO", "disponible": false}
                        """, MediaType.APPLICATION_JSON));

        assertThat(cliente.buscarProducto(PRODUCTO)).get()
                .satisfies(p -> assertThat(p.vendible()).isFalse());
    }

    @Test
    @DisplayName("RN-05: un producto disponible pero no ACTIVO tampoco es vendible")
    void noEsVendibleSiElEstadoNoEsActivo() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withSuccess("""
                        {"id": 1, "comercioId": 10, "nombre": "Pizza muzzarella",
                         "precio": 8500.00, "estado": "PAUSADO", "disponible": true}
                        """, MediaType.APPLICATION_JSON));

        // Catalogo informa estado y disponibilidad por separado; RN-05 necesita las dos.
        assertThat(cliente.buscarProducto(PRODUCTO)).get()
                .satisfies(p -> assertThat(p.vendible()).isFalse());
    }

    @Test
    @DisplayName("INT-01: 404 es producto inexistente o inactivo, no dependencia caida")
    void devuelveVacioSiCatalogoResponde404() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withResourceNotFound());

        assertThat(cliente.buscarProducto(PRODUCTO)).isEmpty();
    }

    @Test
    @DisplayName("INT-01: 5xx de Catalogo es DEPENDENCY_UNAVAILABLE")
    void traduceElErrorDelServidorA503() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withServerError());

        assertThat(catchThrowable(() -> cliente.buscarProducto(PRODUCTO)))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining(CatalogoClientHttp.MODULO);
    }

    @Test
    @DisplayName("INT-01: si Catalogo no responde a tiempo, DEPENDENCY_UNAVAILABLE")
    void traduceElTimeoutA503() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(catchThrowable(() -> cliente.buscarProducto(PRODUCTO)))
                .isInstanceOf(DependenciaNoDisponibleException.class)
                .hasMessageContaining(CatalogoClientHttp.MODULO);
    }

    @Test
    @DisplayName("INT-01: un cuerpo que no se puede leer no se interpreta, corta en 503")
    void traduceElCuerpoIlegibleA503() {
        catalogo.expect(requestTo(URL_PRODUCTO))
                .andRespond(withSuccess("<html>mantenimiento</html>", MediaType.APPLICATION_JSON));

        // RN-03: nunca se sigue con un precio supuesto.
        assertThat(catchThrowable(() -> cliente.buscarProducto(PRODUCTO)))
                .isInstanceOf(DependenciaNoDisponibleException.class);
    }
}
