package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.CartItem;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.CartRepository;
import com.perfume.rasa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for user cart persistence.
 *
 * All endpoints are under /api/cart and require an active session (authenticated).
 *
 * GET    /api/cart              → return all cart items for current user
 * POST   /api/cart              → add or update a cart item (upsert by cartKey)
 * PUT    /api/cart/{cartKey}    → update qty only for an existing item
 * DELETE /api/cart/{cartKey}    → remove a single item by cartKey
 * DELETE /api/cart              → clear the entire cart
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final UserService userService;

    public CartController(CartRepository cartRepository, UserService userService) {
        this.cartRepository = cartRepository;
        this.userService = userService;
    }

    /** Get all cart items for the authenticated user. */
    @GetMapping
    public ResponseEntity<ApiResponse> getCart(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        List<CartItem> items = cartRepository.findByUserId(user.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Cart fetched successfully", items));
    }

    /**
     * Add a new item or update qty/price if cartKey already exists (upsert).
     * The request body should match the frontend CartEngine item shape:
     *   { cartKey, productId/id, name, img, price, qty, size, bottlePrice, bottlePriceDiscount, reuseBottle }
     */
    @PostMapping
    public ResponseEntity<ApiResponse> upsertCartItem(Authentication auth,
                                                       @RequestBody Map<String, Object> body) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());

        String cartKey = getString(body, "_key");
        if (cartKey == null) cartKey = getString(body, "cartKey");
        if (cartKey == null) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "cartKey/_key is required"));
        }

        Optional<CartItem> existing = cartRepository.findByUserIdAndCartKey(user.getId(), cartKey);
        CartItem item = existing.orElse(new CartItem());

        item.setUser(user);
        item.setCartKey(cartKey);
        item.setProductId(getStringOrFallback(body, "productId", "id", cartKey));
        item.setName(getString(body, "name") != null ? getString(body, "name") : "Unknown");
        item.setImg(getString(body, "img") != null ? getString(body, "img") : "img/product/product1.png");
        item.setPrice(getBigDecimal(body, "price"));
        item.setQty(getInt(body, "qty", 1));
        item.setSize(getString(body, "size"));
        item.setBottlePrice(getBigDecimal(body, "bottlePrice"));
        item.setBottlePriceDiscount(getBigDecimal(body, "bottlePriceDiscount"));
        item.setReuseBottle(getBool(body, "reuseBottle"));
        item.setUpdatedAt(LocalDateTime.now());
        if (existing.isEmpty()) item.setAddedAt(LocalDateTime.now());

        CartItem saved = cartRepository.save(item);
        return ResponseEntity.ok(new ApiResponse(true, "Cart item saved", toResponseMap(saved)));
    }

    /** Update only the qty for a specific cart item. */
    @PutMapping("/{cartKey:.+}")
    @Transactional
    public ResponseEntity<ApiResponse> updateQty(Authentication auth,
                                                  @PathVariable String cartKey,
                                                  @RequestBody Map<String, Object> body) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());

        String decodedKey = decode(cartKey);
        Optional<CartItem> opt = cartRepository.findByUserIdAndCartKey(user.getId(), decodedKey);
        if (opt.isEmpty()) opt = cartRepository.findByUserIdAndCartKey(user.getId(), cartKey);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(new ApiResponse(false, "Item not found"));
        }

        CartItem item = opt.get();
        int newQty = getInt(body, "qty", item.getQty());
        if (newQty < 1) {
            cartRepository.delete(item);
            return ResponseEntity.ok(new ApiResponse(true, "Item removed (qty < 1)"));
        }
        item.setQty(newQty);
        item.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(item);
        return ResponseEntity.ok(new ApiResponse(true, "Qty updated", toResponseMap(item)));
    }

    /** Remove a single item by cartKey. */
    @DeleteMapping("/{cartKey:.+}")
    @Transactional
    public ResponseEntity<ApiResponse> removeItem(Authentication auth,
                                                   @PathVariable String cartKey) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());

        String decodedKey = decode(cartKey);
        Optional<CartItem> opt = cartRepository.findByUserIdAndCartKey(user.getId(), decodedKey);
        if (opt.isEmpty()) opt = cartRepository.findByUserIdAndCartKey(user.getId(), cartKey);
        if (opt.isEmpty()) {
            // Try soft match
            List<CartItem> all = cartRepository.findByUserId(user.getId());
            opt = all.stream()
                    .filter(i -> i.getCartKey().equalsIgnoreCase(decodedKey)
                                 || i.getCartKey().equalsIgnoreCase(cartKey))
                    .findFirst();
        }
        if (opt.isPresent()) {
            cartRepository.delete(opt.get());
            return ResponseEntity.ok(new ApiResponse(true, "Item removed from cart"));
        }
        return ResponseEntity.status(404).body(new ApiResponse(false, "Item not found in cart"));
    }

    /** Clear the entire cart for the current user. */
    @DeleteMapping
    @Transactional
    public ResponseEntity<ApiResponse> clearCart(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        cartRepository.deleteAllByUserId(user.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Cart cleared"));
    }

    // ─────────── helpers ───────────

    private Map<String, Object> toResponseMap(CartItem item) {
        return Map.of(
            "_key",    item.getCartKey(),
            "id",      item.getProductId(),
            "name",    item.getName(),
            "img",     item.getImg(),
            "price",   item.getPrice(),
            "qty",     item.getQty(),
            "size",    item.getSize() != null ? item.getSize() : ""
        );
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private String getStringOrFallback(Map<String, Object> m, String key1, String key2, String fallback) {
        String v = getString(m, key1);
        if (v == null) v = getString(m, key2);
        return v != null ? v : fallback;
    }

    private java.math.BigDecimal getBigDecimal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(v.toString()); } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    private int getInt(Map<String, Object> m, String key, int defaultVal) {
        Object v = m.get(key);
        if (v == null) return defaultVal;
        try { return ((Number) v).intValue(); } catch (Exception e) {
            try { return Integer.parseInt(v.toString()); } catch (Exception ex) { return defaultVal; }
        }
    }

    private boolean getBool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(v.toString());
    }

    private String decode(String s) {
        try { return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return s; }
    }
}
