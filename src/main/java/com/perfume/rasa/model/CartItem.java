package com.perfume.rasa.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "cart_key"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    /** Frontend composite key: productId + '_' + size  e.g. "Hugo Boss_50ml" */
    @Column(name = "cart_key", nullable = false)
    private String cartKey;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String img;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int qty = 1;

    @Column
    private String size;

    @Column(name = "bottle_price", precision = 10, scale = 2)
    private BigDecimal bottlePrice;

    @Column(name = "bottle_price_discount", precision = 10, scale = 2)
    private BigDecimal bottlePriceDiscount;

    @Column(name = "reuse_bottle")
    private boolean reuseBottle = false;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public CartItem() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCartKey() { return cartKey; }
    public void setCartKey(String cartKey) { this.cartKey = cartKey; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImg() { return img; }
    public void setImg(String img) { this.img = img; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public BigDecimal getBottlePrice() { return bottlePrice; }
    public void setBottlePrice(BigDecimal bottlePrice) { this.bottlePrice = bottlePrice; }

    public BigDecimal getBottlePriceDiscount() { return bottlePriceDiscount; }
    public void setBottlePriceDiscount(BigDecimal bottlePriceDiscount) { this.bottlePriceDiscount = bottlePriceDiscount; }

    public boolean isReuseBottle() { return reuseBottle; }
    public void setReuseBottle(boolean reuseBottle) { this.reuseBottle = reuseBottle; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
