package org.deliverygo.ordersms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracion de springdoc-openapi: la documentacion viva del modulo, accesible en
 * {@code /swagger-ui.html} (regla de arquitectura: Swagger disponible durante la demo).
 *
 * <p>Los endpoints se documentan con anotaciones en {@code PedidoController} y en los
 * DTOs. Aca va la portada de la API y los ejemplos de error, que se declaran una sola vez
 * en {@code components.examples} y los endpoints referencian por nombre: el formato de
 * error es el mismo para todo el modulo y repetirlo en cada respuesta lo desincroniza.
 */
@Configuration
public class OpenApiConfig {

    /** Prefijo con el que {@code PedidoController} referencia estos ejemplos. */
    public static final String REF_EJEMPLOS = "#/components/examples/";

    @Bean
    public OpenAPI apiPedidos() {
        return new OpenAPI()
                .info(new Info()
                        .title("DeliveryGo — Modulo Pedidos")
                        .version("v1")
                        .description("""
                                Ciclo funcional de la compra: crear el pedido, cargar items, confirmarlo, \
                                cancelarlo y consultarlo (CU-01 a CU-05).

                                El modulo es dueño de Pedido, ItemPedido e HistorialEstado. Usuarios, \
                                productos, pagos y entregas se referencian por ID y se validan por REST \
                                (INT-01 a INT-04).

                                Estado actual: los cinco CU CORE implementados, con las integraciones \
                                respondiendo con mocks declarados (perfil distinto de `integracion`).

                                Datos de prueba de los mocks:
                                * cliente activo `11111111-1111-1111-1111-111111111111`, direccion \
                                `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`
                                * cliente dado de baja `22222222-2222-2222-2222-222222222222` (RN-01)
                                * comercios `99999999-…` (pizzeria) y `88888888-…` (kiosco)
                                * productos de la pizzeria: pizza `33333333-…` (8500), empanada \
                                `44444444-…` (1200) y helado `66666666-…` (sin stock, RN-05)
                                * producto del kiosco: gaseosa `55555555-…` (sirve para RN-02)
                                * Pagos rechaza los cobros de 100000 en adelante (12 pizzas alcanzan) \
                                y reembolsa siempre
                                """)
                        .contact(new Contact().name("Grupo 5 — Aplicaciones Interactivas (UADE)"))
                        .license(new License().name("Uso academico")))
                .components(new Components()
                        .addExamples("VALIDATION_ERROR", ejemplo(400, "VALIDATION_ERROR",
                                "items[0].cantidad: cantidad no puede ser negativa",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/items",
                                "Dato faltante o invalido; tambien cliente o direccion no validos (RN-01, RN-06)"))
                        .addExamples("ORDER_NOT_FOUND", ejemplo(404, "ORDER_NOT_FOUND",
                                "No existe un pedido 3eaaefa8-b9e7-447f-9508-63035c0d874a para el cliente indicado",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a",
                                "El pedido no existe o es de otro cliente: los dos casos responden igual"))
                        .addExamples("ORDER_INVALID_STATE", ejemplo(409, "ORDER_INVALID_STATE",
                                "Transicion no permitida de CONFIRMADO a BORRADOR",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/items",
                                "Transicion que la maquina de estados no permite (RN-04)"))
                        .addExamples("MIXED_MERCHANT", ejemplo(422, "MIXED_MERCHANT",
                                "El producto 55555555-5555-5555-5555-555555555555 es del comercio "
                                        + "88888888-8888-8888-8888-888888888888 y el pedido es del comercio "
                                        + "99999999-9999-9999-9999-999999999999",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/items",
                                "Producto de otro comercio (RN-02)"))
                        .addExamples("PRODUCT_UNAVAILABLE", ejemplo(422, "PRODUCT_UNAVAILABLE",
                                "El producto 66666666-6666-6666-6666-666666666666 no esta disponible",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/items",
                                "Producto inactivo o sin stock en Catalogo (RN-05)"))
                        .addExamples("PRICE_CHANGED", ejemplo(422, "PRICE_CHANGED",
                                "El precio del producto 33333333-3333-3333-3333-333333333333 cambio de "
                                        + "8500.00 a 9200.00",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/confirm",
                                "Al confirmar, un precio dejo de coincidir con el congelado (RN-03)"))
                        .addExamples("PAYMENT_REJECTED", ejemplo(422, "PAYMENT_REJECTED",
                                "Pagos rechazo el cobro del pedido 3eaaefa8-b9e7-447f-9508-63035c0d874a; "
                                        + "el pedido sigue en BORRADOR",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/confirm",
                                "Pagos rechazo el cobro, o no reembolso al cancelar"))
                        .addExamples("DEPENDENCY_UNAVAILABLE", ejemplo(503, "DEPENDENCY_UNAVAILABLE",
                                "El modulo Catalogo no esta disponible",
                                "/api/v1/orders/3eaaefa8-b9e7-447f-9508-63035c0d874a/items",
                                "Catalogo, Usuarios o Pagos no respondio; nunca se sigue con un dato supuesto")));
    }

    /**
     * Arma un ejemplo de {@code ErrorResponse} con el formato normalizado del modulo.
     *
     * <p>El {@code timestamp} y el {@code correlationId} son fijos a proposito: son
     * documentacion, no datos.
     */
    private Example ejemplo(int status, String code, String message, String path, String resumen) {
        // LinkedHashMap y no Map.of: el ejemplo se tiene que ver con los campos en el mismo
        // orden que los declara ErrorResponse.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", "2025-10-27T21:57:49.211Z");
        cuerpo.put("status", status);
        cuerpo.put("code", code);
        cuerpo.put("message", message);
        cuerpo.put("path", path);
        cuerpo.put("correlationId", "0affd3bd-9ef5-4fe2-9057-2f74cd3b179d");
        return new Example().summary(resumen).value(cuerpo);
    }
}
