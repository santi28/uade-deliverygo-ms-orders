package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * INT-01 — Mock de Catalogo (Eq. 2). Declarado como mock; lo reemplaza
 * {@code CatalogoClientHttp} con el perfil {@code integracion}.
 *
 * <p>Los precios son fijos: asi CU-03 puede comparar el precio congelado contra el
 * vigente sin que el resultado dependa del momento de la prueba (RN-03).
 *
 * <p>Imita la forma real de la respuesta de Catalogo, con {@code estado} y
 * {@code disponible} por separado.
 */
@Component
@Profile("!integracion")
public class CatalogoClientMock implements CatalogoClient {

    private static final String ACTIVO = "ACTIVO";

    private static final Map<Long, ProductoDto> PRODUCTOS = Map.of(
            DatosMock.PRODUCTO_PIZZA,
            new ProductoDto(DatosMock.PRODUCTO_PIZZA, DatosMock.COMERCIO_PIZZERIA, "Pizza muzzarella",
                    new BigDecimal("8500.00"), ACTIVO, true),
            DatosMock.PRODUCTO_EMPANADA,
            new ProductoDto(DatosMock.PRODUCTO_EMPANADA, DatosMock.COMERCIO_PIZZERIA, "Empanada de carne",
                    new BigDecimal("1200.00"), ACTIVO, true),
            DatosMock.PRODUCTO_GASEOSA,
            new ProductoDto(DatosMock.PRODUCTO_GASEOSA, DatosMock.COMERCIO_KIOSCO, "Gaseosa 1.5 L",
                    new BigDecimal("2500.00"), ACTIVO, true),
            DatosMock.PRODUCTO_HELADO,
            new ProductoDto(DatosMock.PRODUCTO_HELADO, DatosMock.COMERCIO_PIZZERIA, "Helado 1 kg",
                    new BigDecimal("6000.00"), ACTIVO, false));

    @Override
    public Optional<ProductoDto> buscarProducto(Long productoId) {
        return Optional.ofNullable(PRODUCTOS.get(productoId));
    }
}
