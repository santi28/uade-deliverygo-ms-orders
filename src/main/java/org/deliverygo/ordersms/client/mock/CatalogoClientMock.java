package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-01 — Mock de Catalogo (Eq. 2). Declarado como mock hasta la Etapa 4.
 *
 * <p>Los precios son fijos: asi CU-03 puede comparar el precio congelado contra el
 * "vigente" sin que el resultado dependa del momento de la prueba (RN-03).
 */
@Component
@Profile("!integracion")
public class CatalogoClientMock implements CatalogoClient {

    private static final Map<UUID, ProductoDto> PRODUCTOS = Map.of(
            DatosMock.PRODUCTO_PIZZA,
            new ProductoDto(DatosMock.PRODUCTO_PIZZA, DatosMock.COMERCIO_PIZZERIA, "Pizza muzzarella",
                    new BigDecimal("8500.00"), true),
            DatosMock.PRODUCTO_EMPANADA,
            new ProductoDto(DatosMock.PRODUCTO_EMPANADA, DatosMock.COMERCIO_PIZZERIA, "Empanada de carne",
                    new BigDecimal("1200.00"), true),
            DatosMock.PRODUCTO_GASEOSA,
            new ProductoDto(DatosMock.PRODUCTO_GASEOSA, DatosMock.COMERCIO_KIOSCO, "Gaseosa 1.5 L",
                    new BigDecimal("2500.00"), true),
            DatosMock.PRODUCTO_HELADO,
            new ProductoDto(DatosMock.PRODUCTO_HELADO, DatosMock.COMERCIO_PIZZERIA, "Helado 1 kg",
                    new BigDecimal("6000.00"), false));

    @Override
    public Optional<ProductoDto> buscarProducto(UUID productoId) {
        return Optional.ofNullable(PRODUCTOS.get(productoId));
    }
}
