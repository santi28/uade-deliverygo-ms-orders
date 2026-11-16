package org.deliverygo.ordersms.dto;
import java.util.List;
/** DTO-06 — Contrato estable de paginacion de CU-06. */
public record PaginaPedidosResponse(List<PedidoResumenResponse> pedidos, int pagina,
        int tamanio, long totalElementos, int totalPaginas) {}
