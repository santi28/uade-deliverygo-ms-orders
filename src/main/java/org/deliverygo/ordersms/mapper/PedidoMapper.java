package org.deliverygo.ordersms.mapper;

import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.ItemPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce el agregado {@link Pedido} a DTO-02.
 *
 * <p>Evita que las entidades salgan por el controller y que las colecciones LAZY se
 * serialicen fuera de la transaccion.
 */
@Component
public class PedidoMapper {

    /**
     * Arma DTO-02 con el historial que se le pase (REP-04), en vez de leer
     * {@code pedido.getHistorial()}: asi CU-05 controla el orden cronologico y CU-01
     * puede responder sin cargar la coleccion.
     */
    public PedidoResponse aResponse(Pedido pedido, List<HistorialEstado> historial) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getClienteId(),
                pedido.getComercioId(),
                pedido.getDireccionEntregaId(),
                pedido.getEstado(),
                pedido.getTotal(),
                pedido.getMoneda(),
                pedido.getPagoId(),
                pedido.getFechaCreacion(),
                pedido.getFechaActualizacion(),
                pedido.getItems().stream().map(this::aItemResponse).toList(),
                historial.stream().map(this::aHistorialResponse).toList());
    }

    private PedidoResponse.ItemResponse aItemResponse(ItemPedido item) {
        return new PedidoResponse.ItemResponse(
                item.getId(),
                item.getProductoId(),
                item.getNombreProducto(),
                item.getCantidad(),
                item.getPrecioUnitario(),
                item.getSubtotal());
    }

    private PedidoResponse.HistorialResponse aHistorialResponse(HistorialEstado entrada) {
        return new PedidoResponse.HistorialResponse(
                entrada.getEstadoAnterior(),
                entrada.getEstadoNuevo(),
                entrada.getFechaCambio(),
                entrada.getMotivo());
    }
}
