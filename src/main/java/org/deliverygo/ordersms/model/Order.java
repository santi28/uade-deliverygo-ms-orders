package org.deliverygo.ordersms.model;

import jakarta.persistence.*;

@Entity(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
