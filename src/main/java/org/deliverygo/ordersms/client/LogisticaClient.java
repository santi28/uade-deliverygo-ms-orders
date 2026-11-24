package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.EntregaDto;
import org.deliverygo.ordersms.client.dto.SolicitudEntregaDto;

/**
 * INT-06 — Cliente de Logistica, {@code POST /api/v1/deliveries/assign}.
 *
 * <p>Usado por CU-10 para pedir la entrega de un pedido confirmado. Pedidos no asigna
 * repartidores: solo hace la solicitud y registra lo que responde Logistica.
 *
 * <p>Hoy solo tiene implementacion mock: el contrato publicado pide los ids de pedido y
 * cliente como {@code int64} (los nuestros son UUID) y direcciones en texto que ningun
 * modulo expone. Desvio registrado en PLAN.md (Etapa 6). Si Logistica no responde se
 * lanza {@code DependenciaNoDisponibleException} y el pedido sigue CONFIRMADO.
 */
public interface LogisticaClient {

    /** INT-06 — Crea o reutiliza la entrega del pedido; idempotente por pedido. */
    EntregaDto solicitarAsignacion(SolicitudEntregaDto solicitud);
}
