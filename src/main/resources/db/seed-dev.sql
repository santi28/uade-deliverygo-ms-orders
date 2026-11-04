-- Datos de prueba del perfil `dev`: pedidos deterministas para la demo y para probar
-- desde Swagger sin tener que armar uno a mano.
--
-- Lo carga Spring despues de que Hibernate crea el esquema (ddl-auto=update), por
-- `spring.sql.init.data-locations` en application-dev.properties. El perfil `test` no lo
-- usa: los tests arman sus propios datos.
--
-- Los ids de cliente, comercio y productos son los de DatosMock, para que un pedido
-- sembrado se pueda confirmar o cancelar con los mocks de INT-01..04 sin tocar nada.
--
-- Cada INSERT es idempotente (`WHERE NOT EXISTS`): el script corre en cada arranque y la
-- base del compose es un volumen que sobrevive al `down`.
--
-- Que NO se siembra: un pedido en BORRADOR. CU-01 devuelve el borrador abierto del
-- cliente en vez de crear otro (REP-01), asi que un borrador sembrado haria que el
-- primer POST de la demo respondiera 200 en lugar de 201.

-- Pedido CONFIRMADO y cobrado: sirve para CU-05 (ver historial completo) y para CU-04
-- con reembolso, porque tiene pago asociado.
INSERT INTO pedidos (id, cliente_id, comercio_id, direccion_entrega_id, estado, total, moneda,
                     pago_id, fecha_creacion, fecha_actualizacion, version)
SELECT 'dddd0001-0000-4000-8000-000000000001',
       '11111111-1111-1111-1111-111111111111',
       10,
       'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
       'CONFIRMADO',
       20600.00,
       'ARS',
       'dddddddd-dddd-dddd-dddd-dddddddddddd',
       TIMESTAMP WITH TIME ZONE '2026-11-03 12:00:00+00',
       TIMESTAMP WITH TIME ZONE '2026-11-03 12:05:00+00',
       0
WHERE NOT EXISTS (SELECT 1 FROM pedidos WHERE id = 'dddd0001-0000-4000-8000-000000000001');

-- Pedido CANCELADO: sirve para mostrar el 409 ORDER_INVALID_STATE al intentar
-- confirmarlo o volver a cancelarlo (RN-04).
INSERT INTO pedidos (id, cliente_id, comercio_id, direccion_entrega_id, estado, total, moneda,
                     pago_id, fecha_creacion, fecha_actualizacion, version)
SELECT 'dddd0002-0000-4000-8000-000000000002',
       '11111111-1111-1111-1111-111111111111',
       10,
       'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
       'CANCELADO',
       8500.00,
       'ARS',
       NULL,
       TIMESTAMP WITH TIME ZONE '2026-11-03 13:00:00+00',
       TIMESTAMP WITH TIME ZONE '2026-11-03 13:10:00+00',
       0
WHERE NOT EXISTS (SELECT 1 FROM pedidos WHERE id = 'dddd0002-0000-4000-8000-000000000002');

-- Lineas del pedido confirmado, con el precio congelado al agregarlas (RN-03, DM-01).
INSERT INTO items_pedido (id, pedido_id, producto_id, nombre_producto, cantidad, precio_unitario)
SELECT 'eeee0001-0000-4000-8000-000000000001',
       'dddd0001-0000-4000-8000-000000000001',
       1,
       'Pizza muzzarella',
       2,
       8500.00
WHERE NOT EXISTS (SELECT 1 FROM items_pedido WHERE id = 'eeee0001-0000-4000-8000-000000000001');

INSERT INTO items_pedido (id, pedido_id, producto_id, nombre_producto, cantidad, precio_unitario)
SELECT 'eeee0002-0000-4000-8000-000000000002',
       'dddd0001-0000-4000-8000-000000000001',
       2,
       'Empanada de carne',
       3,
       1200.00
WHERE NOT EXISTS (SELECT 1 FROM items_pedido WHERE id = 'eeee0002-0000-4000-8000-000000000002');

-- Linea del pedido cancelado.
INSERT INTO items_pedido (id, pedido_id, producto_id, nombre_producto, cantidad, precio_unitario)
SELECT 'eeee0003-0000-4000-8000-000000000003',
       'dddd0002-0000-4000-8000-000000000002',
       1,
       'Pizza muzzarella',
       1,
       8500.00
WHERE NOT EXISTS (SELECT 1 FROM items_pedido WHERE id = 'eeee0003-0000-4000-8000-000000000003');

-- Historial del pedido confirmado (REL-02), en el orden que devuelve REP-04.
INSERT INTO historial_estados (id, pedido_id, estado_anterior, estado_nuevo, fecha_cambio, motivo)
SELECT 'ffff0001-0000-4000-8000-000000000001',
       'dddd0001-0000-4000-8000-000000000001',
       'BORRADOR', 'BORRADOR',
       TIMESTAMP WITH TIME ZONE '2026-11-03 12:00:00+00',
       'Pedido creado como borrador'
WHERE NOT EXISTS (SELECT 1 FROM historial_estados WHERE id = 'ffff0001-0000-4000-8000-000000000001');

INSERT INTO historial_estados (id, pedido_id, estado_anterior, estado_nuevo, fecha_cambio, motivo)
SELECT 'ffff0002-0000-4000-8000-000000000002',
       'dddd0001-0000-4000-8000-000000000001',
       'BORRADOR', 'BORRADOR',
       TIMESTAMP WITH TIME ZONE '2026-11-03 12:02:00+00',
       'CU-02: 2 linea(s) agregada(s)'
WHERE NOT EXISTS (SELECT 1 FROM historial_estados WHERE id = 'ffff0002-0000-4000-8000-000000000002');

INSERT INTO historial_estados (id, pedido_id, estado_anterior, estado_nuevo, fecha_cambio, motivo)
SELECT 'ffff0003-0000-4000-8000-000000000003',
       'dddd0001-0000-4000-8000-000000000001',
       'BORRADOR', 'CONFIRMADO',
       TIMESTAMP WITH TIME ZONE '2026-11-03 12:05:00+00',
       'CU-03: pago APROBADO dddddddd-dddd-dddd-dddd-dddddddddddd'
WHERE NOT EXISTS (SELECT 1 FROM historial_estados WHERE id = 'ffff0003-0000-4000-8000-000000000003');

-- Historial del pedido cancelado.
INSERT INTO historial_estados (id, pedido_id, estado_anterior, estado_nuevo, fecha_cambio, motivo)
SELECT 'ffff0004-0000-4000-8000-000000000004',
       'dddd0002-0000-4000-8000-000000000002',
       'BORRADOR', 'BORRADOR',
       TIMESTAMP WITH TIME ZONE '2026-11-03 13:00:00+00',
       'Pedido creado como borrador'
WHERE NOT EXISTS (SELECT 1 FROM historial_estados WHERE id = 'ffff0004-0000-4000-8000-000000000004');

INSERT INTO historial_estados (id, pedido_id, estado_anterior, estado_nuevo, fecha_cambio, motivo)
SELECT 'ffff0005-0000-4000-8000-000000000005',
       'dddd0002-0000-4000-8000-000000000002',
       'BORRADOR', 'CANCELADO',
       TIMESTAMP WITH TIME ZONE '2026-11-03 13:10:00+00',
       'CU-04: cancelado desde BORRADOR, sin reembolso'
WHERE NOT EXISTS (SELECT 1 FROM historial_estados WHERE id = 'ffff0005-0000-4000-8000-000000000005');
