package com.perfume.rasa.dto;

import java.math.BigDecimal;

public class OrderItemRequestDTO {
    private String productId;
    private String name;
    private BigDecimal price;
    private Integer qty;
    private String size;
    private String bottleType;
    private BigDecimal bottlePrice;

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
}
