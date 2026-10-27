package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ProductoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.deliverygo.ordersms.client.dto.UsuarioDto;
import org.deliverygo.ordersms.dto.ConfirmarPedidoRequest;
import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.ModificarItemsPedidoRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.PedidoNotFoundException;
import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.deliverygo.ordersms.mapper.PedidoMapper;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.ItemPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio de Pedidos: concentra las reglas de negocio de los CU CORE.
 *
 * <p>Cubre los cinco CU CORE: SRV-01 (CU-01), SRV-02 (CU-02), SRV-03 (CU-03), SRV-04
 * (CU-04) y SRV-05 (CU-05).
 */
@Service
public class PedidoService {

    private static final String MOTIVO_CREACION = "Pedido creado como borrador";

    private final PedidoRepository pedidoRepository;
    private final HistorialEstadoRepository historialEstadoRepository;
    private final UsuariosClient usuariosClient;
    private final CatalogoClient catalogoClient;
    private final PagosClient pagosClient;
    private final MaquinaEstados maquinaEstados;
    private final PedidoMapper pedidoMapper;

    /**
     * Transaccion aparte de la del CU, para dejar asentado en el historial un intento de
     * pago rechazado aunque CU-03 termine respondiendo 422 (DM-07).
     */
    private final TransactionTemplate transaccionIndependiente;

    public PedidoService(PedidoRepository pedidoRepository, HistorialEstadoRepository historialEstadoRepository,
            UsuariosClient usuariosClient, CatalogoClient catalogoClient, PagosClient pagosClient,
            MaquinaEstados maquinaEstados, PedidoMapper pedidoMapper,
            PlatformTransactionManager transactionManager) {
        this.pedidoRepository = pedidoRepository;
        this.historialEstadoRepository = historialEstadoRepository;
        this.usuariosClient = usuariosClient;
        this.catalogoClient = catalogoClient;
        this.pagosClient = pagosClient;
        this.maquinaEstados = maquinaEstados;
        this.pedidoMapper = pedidoMapper;
        this.transaccionIndependiente = new TransactionTemplate(transactionManager);
        this.transaccionIndependiente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        validarClienteYDireccion(clienteId, request.direccionEntregaId());

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
     * SRV-02 — CU-02: aplica altas, cambios y bajas de lineas sobre un pedido en
     * BORRADOR y recalcula el total.
     *
     * <p>La cantidad que llega es la cantidad final de la linea, no un delta: si el
     * producto ya estaba se reemplaza, si no estaba se agrega y con cantidad 0 se quita.
     * Los productos que el request no menciona quedan como estaban.
     *
     * <p>Reglas: solo se editan borradores (RN-04, validado antes de tocar el agregado),
     * todas las lineas tienen que ser del comercio del pedido (RN-02) y el producto tiene
     * que estar disponible en Catalogo (RN-05, INT-01). El precio lo pone Catalogo y lo
     * congela {@code ItemPedido} (RN-03): nunca llega desde el request.
     *
     * <p>Usa REP-02 para traer el pedido con sus items en una sola consulta y despues
     * verifica el titular en memoria: un pedido ajeno responde ORDER_NOT_FOUND igual que
     * uno inexistente.
     */
    @Transactional
    public PedidoResponse gestionarItems(UUID pedidoId, UUID clienteId, ModificarItemsPedidoRequest request) {
        // REP-02: pedido + items, sin N+1 al recalcular el total.
        Pedido pedido = pedidoRepository.findByIdConItems(pedidoId)
                .filter(encontrado -> encontrado.getClienteId().equals(clienteId))
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        // RN-04: un pedido ya confirmado o cancelado no se edita. Se valida antes de tocar
        // el agregado para no dejarlo a medio modificar.
        maquinaEstados.validar(pedido.getEstado(), EstadoPedido.BORRADOR);

        validarProductosSinRepetir(request.items());

        int agregados = 0;
        int modificados = 0;
        int quitados = 0;
        for (ModificarItemsPedidoRequest.LineaItem linea : request.items()) {
            switch (aplicarLinea(pedido, linea)) {
                case AGREGADO -> agregados++;
                case MODIFICADO -> modificados++;
                case QUITADO -> quitados++;
                case SIN_CAMBIOS -> {
                    // Pedir la baja de un producto que no esta en el pedido no es un error:
                    // CU-02 es idempotente para que reintentar la misma llamada no falle.
                }
            }
        }

        // RN-04: la edicion de un borrador queda asentada en el historial (BORRADOR a
        // BORRADOR). Una llamada que no cambio ninguna linea no deja entrada: el historial
        // es lo que lee CU-05 y no tiene por que llenarse de reintentos sin efecto.
        if (agregados + modificados + quitados > 0) {
            maquinaEstados.transicionar(pedido, EstadoPedido.BORRADOR,
                    "CU-02: " + agregados + " agregados, " + modificados + " modificados, "
                            + quitados + " quitados");
        }

        return pedidoMapper.aResponse(pedido, leerHistorial(pedidoId));
    }

    /**
     * SRV-03 — CU-03: confirma el pedido cobrandolo, y lo pasa a CONFIRMADO en la misma
     * transaccion.
     *
     * <p>Antes de cobrar revalida todo lo que pudo cambiar mientras el borrador estaba
     * abierto: que el cliente siga activo y la direccion siga siendo suya (RN-01, RN-06,
     * INT-02) y que cada linea siga disponible y al mismo precio (RN-05, RN-03, INT-01).
     * Un pedido sin lineas no se confirma.
     *
     * <p>El cobro es INT-03, con el estado resuelto en la misma respuesta:
     *
     * <ul>
     *   <li>APROBADO: se guarda el {@code pagoId} (DM-09) y se pasa a CONFIRMADO.
     *   <li>RECHAZADO: el pedido sigue en BORRADOR y se responde 422 PAYMENT_REJECTED. El
     *       intento queda asentado en el historial (DM-07).
     *   <li>Si Pagos no responde, el pedido sigue en BORRADOR y se responde 503: nunca se
     *       confirma sin cobro.
     * </ul>
     */
    @Transactional
    public PedidoResponse confirmarPedido(UUID pedidoId, UUID clienteId, ConfirmarPedidoRequest request) {
        // REP-02: pedido + items, que es lo que hay que revalidar y lo que define el total.
        Pedido pedido = pedidoRepository.findByIdConItems(pedidoId)
                .filter(encontrado -> encontrado.getClienteId().equals(clienteId))
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        // RN-04: solo se confirma un borrador. Se valida antes de cobrar.
        maquinaEstados.validar(pedido.getEstado(), EstadoPedido.CONFIRMADO);

        if (pedido.getItems().isEmpty()) {
            throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                    "El pedido " + pedidoId + " no tiene items para confirmar");
        }

        // RN-01, RN-06: el cliente y la direccion pudieron cambiar desde CU-01.
        validarClienteYDireccion(clienteId, pedido.getDireccionEntregaId());

        revalidarLineasContraCatalogo(pedido);

        // INT-03: si Pagos no responde, la excepcion sale sin tocar el estado del pedido.
        PagoDto pago = pagosClient.cobrar(new SolicitudPagoDto(
                pedido.getId(),
                clienteId,
                pedido.getTotal(),
                pedido.getMoneda(),
                request.medioPago()));

        if (pago.status() == EstadoPago.RECHAZADO) {
            // DM-07: el rechazo queda en el historial aunque el CU responda 422. Va en una
            // transaccion aparte porque la del CU se deshace al lanzar la excepcion.
            registrarPagoRechazado(pedidoId, pago.paymentId());
            throw new ReglaNegocioException(CodigoError.PAYMENT_REJECTED,
                    "Pagos rechazo el cobro del pedido " + pedidoId + "; el pedido sigue en BORRADOR");
        }

        if (pago.status() != EstadoPago.APROBADO) {
            // El contrato con Eq. 4 dice que el cobro se resuelve en la misma llamada: un
            // estado distinto es un incumplimiento, no un rechazo, y no se confirma nada.
            throw new ReglaNegocioException(CodigoError.DEPENDENCY_UNAVAILABLE,
                    "Pagos respondio un estado inesperado al cobrar: " + pago.status());
        }

        pedido.asociarPago(pago.paymentId());
        maquinaEstados.transicionar(pedido, EstadoPedido.CONFIRMADO, "CU-03: pago APROBADO " + pago.paymentId());

        return pedidoMapper.aResponse(pedido, leerHistorial(pedidoId));
    }

    /**
     * SRV-04 — CU-04: cancela el pedido.
     *
     * <p>Un borrador se cancela sin mas. Un pedido ya CONFIRMADO exige reembolso: se pide
     * a Pagos por INT-04 y solo pasa a CANCELADO si responde REEMBOLSADO. Si Pagos no
     * responde, el pedido queda como estaba (503).
     *
     * <p>Desde cualquier otro estado la cancelacion no esta permitida (RN-04) y responde
     * ORDER_INVALID_STATE.
     */
    @Transactional
    public PedidoResponse cancelarPedido(UUID pedidoId, UUID clienteId) {
        // REP-03: pedido verificando el titular.
        Pedido pedido = pedidoRepository.findByIdAndClienteId(pedidoId, clienteId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        // RN-04: estados cancelables. Se valida antes de pedir el reembolso.
        maquinaEstados.validar(pedido.getEstado(), EstadoPedido.CANCELADO);

        String motivo = "CU-04: cancelado desde BORRADOR, sin reembolso";

        if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
            if (pedido.getPagoId() == null) {
                // No deberia pasar: CU-03 confirma y asocia el pago en la misma transaccion.
                // Si pasa, es inconsistencia de datos y no se cancela a ciegas.
                throw new ReglaNegocioException(CodigoError.ORDER_INVALID_STATE,
                        "El pedido " + pedidoId + " esta CONFIRMADO y no tiene un pago asociado");
            }

            // INT-04: si no responde, la excepcion sale sin cambiar el estado del pedido.
            PagoDto reembolso = pagosClient.reembolsar(pedido.getPagoId(), pedido.getId());

            if (reembolso.status() != EstadoPago.REEMBOLSADO) {
                throw new ReglaNegocioException(CodigoError.PAYMENT_REJECTED,
                        "Pagos no reembolso el pedido " + pedidoId + " (respondio " + reembolso.status()
                                + "); el pedido sigue CONFIRMADO");
            }

            motivo = "CU-04: cancelado con reembolso del pago " + pedido.getPagoId();
        }

        maquinaEstados.transicionar(pedido, EstadoPedido.CANCELADO, motivo);

        return pedidoMapper.aResponse(pedido, leerHistorial(pedidoId));
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

    /**
     * INT-02 — RN-01 y RN-06: el cliente existe y esta activo, y la direccion es una de
     * las suyas.
     *
     * <p>Lo usan CU-01 y CU-03: al confirmar hay que volver a preguntar, porque el
     * borrador pudo quedar abierto mucho tiempo.
     */
    private void validarClienteYDireccion(UUID clienteId, UUID direccionEntregaId) {
        UsuarioDto usuario = usuariosClient.buscarUsuario(clienteId)
                .orElseThrow(() -> new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                        "El cliente " + clienteId + " no esta registrado"));

        // RN-01: el pedido pertenece a un usuario registrado y activo.
        if (!usuario.activo()) {
            throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                    "El cliente " + clienteId + " no esta activo");
        }

        // RN-06: la direccion de entrega tiene que ser una de las del usuario.
        if (!usuario.tieneDireccion(direccionEntregaId)) {
            throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                    "La direccion " + direccionEntregaId + " no pertenece al cliente");
        }
    }

    /**
     * INT-01 — CU-03: revalida cada linea contra Catalogo antes de cobrar.
     *
     * <p>El precio congelado tiene que seguir siendo el vigente (RN-03): si cambio, el
     * cliente tiene que ver el total nuevo antes de pagar, asi que se corta con
     * PRICE_CHANGED y el pedido queda en BORRADOR.
     */
    private void revalidarLineasContraCatalogo(Pedido pedido) {
        for (ItemPedido item : pedido.getItems()) {
            ProductoDto producto = catalogoClient.buscarProducto(item.getProductoId())
                    .orElseThrow(() -> new ReglaNegocioException(CodigoError.PRODUCT_UNAVAILABLE,
                            "El producto " + item.getProductoId() + " ya no esta en Catalogo"));

            // RN-02: el producto pudo pasar a otro comercio desde que se agrego.
            if (!producto.comercioId().equals(pedido.getComercioId())) {
                throw new ReglaNegocioException(CodigoError.MIXED_MERCHANT,
                        "El producto " + item.getProductoId() + " ya no es del comercio del pedido");
            }

            // RN-05: solo productos activos y disponibles.
            if (!producto.disponible()) {
                throw new ReglaNegocioException(CodigoError.PRODUCT_UNAVAILABLE,
                        "El producto " + item.getProductoId() + " no esta disponible");
            }

            // RN-03: el total se cobra con precios confirmados.
            if (producto.precio().compareTo(item.getPrecioUnitario()) != 0) {
                throw new ReglaNegocioException(CodigoError.PRICE_CHANGED,
                        "El precio del producto " + item.getProductoId() + " cambio de "
                                + item.getPrecioUnitario() + " a " + producto.precio());
            }
        }
    }

    /**
     * DM-07 — Deja el intento de pago rechazado en el historial del pedido, que sigue en
     * BORRADOR.
     *
     * <p>Corre en una transaccion propia: la del CU se deshace al lanzar
     * PAYMENT_REJECTED y se llevaria esta entrada con ella. Si no se puede asentar, el
     * CU responde igual 422: el rechazo del cobro es lo que el cliente tiene que ver.
     */
    private void registrarPagoRechazado(UUID pedidoId, UUID paymentId) {
        transaccionIndependiente.executeWithoutResult(status ->
                pedidoRepository.findById(pedidoId).ifPresent(pedido -> {
                    maquinaEstados.transicionar(pedido, EstadoPedido.BORRADOR,
                            "CU-03: pago RECHAZADO " + paymentId);
                    pedidoRepository.save(pedido);
                }));
    }

    /** Resultado de aplicar una linea de DTO-03, para resumir el cambio en el historial. */
    private enum ResultadoLinea {
        AGREGADO, MODIFICADO, QUITADO, SIN_CAMBIOS
    }

    /**
     * Aplica una linea de DTO-03 sobre el pedido.
     *
     * <p>La baja no consulta Catalogo: un producto que el comercio dio de baja despues de
     * haberlo agregado igual se tiene que poder sacar del borrador.
     */
    private ResultadoLinea aplicarLinea(Pedido pedido, ModificarItemsPedidoRequest.LineaItem linea) {
        Optional<ItemPedido> existente = pedido.buscarItem(linea.productoId());

        if (linea.cantidad() == 0) {
            if (existente.isEmpty()) {
                return ResultadoLinea.SIN_CAMBIOS;
            }
            pedido.quitarItem(existente.get());
            return ResultadoLinea.QUITADO;
        }

        ProductoDto producto = buscarProductoVendible(pedido, linea.productoId());

        if (existente.isPresent()) {
            // RN-03: la linea conserva el precio congelado al agregarla; si cambio en
            // Catalogo lo detecta CU-03 al confirmar (PRICE_CHANGED).
            existente.get().cambiarCantidad(linea.cantidad());
            pedido.recalcularTotal();
            return ResultadoLinea.MODIFICADO;
        }

        pedido.agregarItem(producto.id(), producto.nombre(), linea.cantidad(), producto.precio());
        return ResultadoLinea.AGREGADO;
    }

    /**
     * INT-01 — Resuelve el producto en Catalogo y verifica que se pueda vender en este
     * pedido: que exista, que sea del mismo comercio (RN-02) y que este disponible
     * (RN-05).
     */
    private ProductoDto buscarProductoVendible(Pedido pedido, UUID productoId) {
        ProductoDto producto = catalogoClient.buscarProducto(productoId)
                .orElseThrow(() -> new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                        "El producto " + productoId + " no existe en Catalogo"));

        // RN-02: todos los productos del pedido son del mismo comercio.
        if (!producto.comercioId().equals(pedido.getComercioId())) {
            throw new ReglaNegocioException(CodigoError.MIXED_MERCHANT,
                    "El producto " + productoId + " es del comercio " + producto.comercioId()
                            + " y el pedido es del comercio " + pedido.getComercioId());
        }

        // RN-05: solo productos activos y disponibles.
        if (!producto.disponible()) {
            throw new ReglaNegocioException(CodigoError.PRODUCT_UNAVAILABLE,
                    "El producto " + productoId + " no esta disponible");
        }

        return producto;
    }

    /**
     * Dos lineas del mismo producto en un request dejarian el resultado a merced del
     * orden de la lista, asi que se rechaza con VALIDATION_ERROR.
     */
    private void validarProductosSinRepetir(List<ModificarItemsPedidoRequest.LineaItem> lineas) {
        Set<UUID> vistos = new HashSet<>();
        for (ModificarItemsPedidoRequest.LineaItem linea : lineas) {
            if (!vistos.add(linea.productoId())) {
                throw new ReglaNegocioException(CodigoError.VALIDATION_ERROR,
                        "El producto " + linea.productoId() + " aparece repetido en el request");
            }
        }
    }

    /** REP-04 — Historial del pedido en orden cronologico. */
    private List<HistorialEstado> leerHistorial(UUID pedidoId) {
        return historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(pedidoId);
    }
}
