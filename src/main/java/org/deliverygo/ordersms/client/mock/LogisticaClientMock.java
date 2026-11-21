package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.LogisticaClient;
import org.deliverygo.ordersms.client.dto.EntregaDto;
import org.deliverygo.ordersms.client.dto.SolicitudEntregaDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * INT-06 — Mock de Logistica. Declarado como mock, sin fecha de reemplazo.
 *
 * <p>Como {@code UsuariosClientMock}, no se apaga con el perfil {@code integracion}: no
 * hay implementacion HTTP porque el contrato de Logistica no es compatible con nuestros
 * ids (pide {@code int64} y los nuestros son UUID). Desvio registrado en PLAN.md
 * (Etapa 6).
 *
 * <p>Imita lo que publica Logistica: crea la entrega en ASIGNADA y, si se pide de nuevo
 * para el mismo pedido, devuelve la misma ("crea o reutiliza").
 */
@Component
public class LogisticaClientMock implements LogisticaClient {

    /** Estado con el que Logistica informa una entrega con repartidor (201). */
    private static final String ASIGNADA = "ASIGNADA";

    private final AtomicLong secuencia = new AtomicLong();

    /** Entregas creadas por este proceso, por pedido. */
    private final Map<UUID, EntregaDto> entregas = new ConcurrentHashMap<>();

    @Override
    public EntregaDto solicitarAsignacion(SolicitudEntregaDto solicitud) {
        return entregas.computeIfAbsent(solicitud.pedidoId(),
                pedido -> new EntregaDto(secuencia.incrementAndGet(), ASIGNADA));
    }
}
