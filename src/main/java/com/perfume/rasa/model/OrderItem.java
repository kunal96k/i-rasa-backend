package com.perfume.rasa.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productId;
    private String name;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    private Integer qty;
    private String size;
    
    // Bottle selection fields
    private String bottleType;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal bottlePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getBottleType() { return bottleType; }
    public void setBottleType(String bottleType) { this.bottleType = bottleType; }
    public BigDecimal getBottlePrice() { return bottlePrice; }
    public void setBottlePrice(BigDecimal bottlePrice) { this.bottlePrice = bottlePrice; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
}
