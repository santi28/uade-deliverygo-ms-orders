package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.client.dto.UsuarioDto;
import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.deliverygo.ordersms.mapper.PedidoMapper;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de Pedidos: concentra las reglas de negocio de los CU CORE.
 *
 * <p>Implementa por ahora SRV-01 (CU-01) y SRV-05 (CU-05); SRV-02, SRV-03 y SRV-04
 * llegan en la Etapa 2.
 */
@Service
public class PedidoService {

    private static final String MOTIVO_CREACION = "Pedido creado como borrador";

    private final PedidoRepository pedidoRepository;
    private final HistorialEstadoRepository historialEstadoRepository;
    private final UsuariosClient usuariosClient;
    private final PedidoMapper pedidoMapper;

    public PedidoService(PedidoRepository pedidoRepository, HistorialEstadoRepository historialEstadoRepository,
            UsuariosClient usuariosClient, PedidoMapper pedidoMapper) {
        this.pedidoRepository = pedidoRepository;
        this.historialEstadoRepository = historialEstadoRepository;
        this.usuariosClient = usuariosClient;
        this.pedidoMapper = pedidoMapper;
    }

    /**
     * SRV-01 — CU-01: crea un pedido en BORRADOR, o devuelve el borrador que el cliente
     * ya tenga abierto.
     *
     * <p>Valida contra Usuarios que el cliente exista y este activo (RN-01) y que la
     * direccion sea suya (RN-06), ambas por INT-02. El pedido nace sin items y con total
     * cero; las lineas se cargan con CU-02.
     *
     * <p>Si el cliente ya tiene un borrador del mismo comercio se devuelve ese, para no
     * dejar borradores huerfanos a cada reintento (REP-01). Si el borrador abierto es de
     * otro comercio se corta con MIXED_MERCHANT: un pedido no puede mezclar comercios
     * (RN-02).
     */
    @Transactional
    public PedidoResponse crearPedido(UUID clienteId, CrearPedidoRequest request) {
        UsuarioDto usuario = usuariosClient.buscarUsuario(clienteId)
                .orElseThrow(() -> new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                        "El cliente " + clienteId + " no esta registrado"));

        // RN-01: el pedido pertenece a un usuario registrado y activo.
        if (!usuario.activo()) {
            throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                    "El cliente " + clienteId + " no esta activo");
        }

        // RN-06: la direccion de entrega tiene que ser una de las del usuario.
        if (!usuario.tieneDireccion(request.direccionEntregaId())) {
            throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                    "La direccion " + request.direccionEntregaId() + " no pertenece al cliente");
        }

        // REP-01: borrador abierto del cliente.
        Optional<Pedido> borradorAbierto = pedidoRepository.findByClienteIdAndEstado(clienteId, EstadoPedido.BORRADOR);
        if (borradorAbierto.isPresent()) {
            Pedido borrador = borradorAbierto.get();
            if (!borrador.getComercioId().equals(request.comercioId())) {
                throw new ReglaNegocioException(CodigoError.MIXED_MERCHANT,
                        "El cliente ya tiene un borrador abierto de otro comercio: " + borrador.getId());
            }
            return pedidoMapper.aResponse(borrador, leerHistorial(borrador.getId()));
        }

        Pedido pedido = Pedido.crear(clienteId, request.comercioId(), request.direccionEntregaId());
        // Deja asentado el nacimiento del pedido en el historial que lee CU-05 (REL-02).
        pedido.registrarTransicion(EstadoPedido.BORRADOR, MOTIVO_CREACION);

        Pedido guardado = pedidoRepository.save(pedido);
        return pedidoMapper.aResponse(guardado, guardado.getHistorial());
    }

    /**
     * SRV-05 — CU-05: devuelve el pedido con sus items y su historial de estados.
     *
     * <p>Usa REP-03 para que un cliente no pueda leer pedidos ajenos: si el pedido no es
     * suyo responde igual que si no existiera (ORDER_NOT_FOUND). El historial se trae con
     * REP-04, aparte de los items, porque un unico JOIN FETCH de las dos colecciones
     * rompe con MultipleBagFetchException.
     */
    @Transactional(readOnly = true)
    public PedidoResponse consultarPedido(UUID pedidoId, UUID clienteId) {
        Pedido pedido = pedidoRepository.findByIdAndClienteId(pedidoId, clienteId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));
        return pedidoMapper.aResponse(pedido, leerHistorial(pedidoId));
    }

    /** REP-04 — Historial del pedido en orden cronologico. */
    private List<HistorialEstado> leerHistorial(UUID pedidoId) {
        return historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(pedidoId);
    }
}
