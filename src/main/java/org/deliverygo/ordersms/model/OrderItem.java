package org.deliverygo.ordersms.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity()
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "order_id", "product_id" }))
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    // Extraer el productId, name, y unitPrice a un DTO.
    public OrderItem(Order order, Long productId, String productName, Integer quantity, BigDecimal unitPrice) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than zero");

        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }
}
