package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * INT-01 — Cliente HTTP de Catalogo (Eq. 2), {@code GET /api/v1/productos/{id}}.
 *
 * <p>Reemplaza a {@code CatalogoClientMock} cuando esta activo el perfil
 * {@code integracion}. Sostiene RN-02 (comercio), RN-03 (precio vigente) y RN-05
 * (disponibilidad) en CU-01, CU-02 y CU-03.
 *
 * <p>Contrato tomado del OpenAPI del ecosistema v1.4.0. Es el unico endpoint de los que
 * consumimos que no exige token.
 */
@Component
@Profile("integracion")
public class CatalogoClientHttp extends ClienteHttpDeModulo implements CatalogoClient {

    static final String MODULO = "Catalogo";

    /** INT-01 — Ficha del producto; el id va como variable de ruta. */
    static final String RUTA_PRODUCTO = "/api/v1/productos/{id}";

    public CatalogoClientHttp(
            RestClient.Builder builder,
            @Value("${deliverygo.integraciones.catalogo.base-url}") String baseUrl) {
        super(MODULO, baseUrl, builder);
    }

    @Override
    public Optional<ProductoDto> buscarProducto(Long productoId) {
        return buscar(() -> rest.get()
                .uri(RUTA_PRODUCTO, productoId)
                .retrieve()
                .body(ProductoDto.class));
    }
}
