package org.deliverygo.ordersms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de springdoc-openapi: la documentacion viva del modulo, accesible en
 * {@code /swagger-ui.html} (regla de arquitectura: Swagger disponible durante la demo).
 *
 * <p>Los endpoints se documentan con anotaciones en {@code PedidoController} y en los
 * DTOs; aca solo va la portada de la API.
 */
@Configuration
public class OpenApiConfig {

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

                                Estado actual: CU-01 y CU-05 implementados; las integraciones responden \
                                con mocks declarados (perfil distinto de `integracion`).

                                Datos de prueba de los mocks:
                                * cliente activo `11111111-1111-1111-1111-111111111111`, direccion \
                                `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`
                                * cliente dado de baja `22222222-2222-2222-2222-222222222222` (RN-01)
                                * comercios `99999999-…` (pizzeria) y `88888888-…` (kiosco)
                                """)
                        .contact(new Contact().name("Grupo 5 — Aplicaciones Interactivas (UADE)"))
                        .license(new License().name("Uso academico")));
    }
}
