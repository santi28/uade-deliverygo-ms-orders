# CU06: listado paginado de pedidos

## API
GET /api/v1/orders?pagina=0&tamanio=20
Cabecera X-Cliente-Id: UUID.

Respuesta: pedidos (id, comercioId, estado, total, moneda, fechaCreacion), pagina, tamanio, totalElementos y totalPaginas.

Página desde 0 y tamaño entre 1 y 100. Desplazamiento máximo: 2147483647. Orden: fechaCreacion DESC e id DESC. Sin resultados: 200 con lista vacía. Parámetros inválidos: 400.

X-Cliente-Id filtra los pedidos, pero no autentica al solicitante; requiere identidad verificada antes de producción.

## Pruebas
./mvnw -Dtest=ListarPedidosServiceTest,ListarPedidosControllerTest,PedidoRepositoryTest test
