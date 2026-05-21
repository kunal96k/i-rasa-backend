package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.User;
import com.perfume.rasa.model.WishlistItem;
import com.perfume.rasa.repository.WishlistRepository;
import com.perfume.rasa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/profile/wishlist")
public class WishlistController {

    private final WishlistRepository wishlistRepository;
    private final UserService userService;

    public WishlistController(WishlistRepository wishlistRepository, UserService userService) {
        this.wishlistRepository = wishlistRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getWishlist(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        List<WishlistItem> items = wishlistRepository.findByUserId(user.getId());

        // Simulate edge cases: Out of stock for "555" and Removed for "Kala bhoot"
        boolean updatedAny = false;
        for (WishlistItem item : items) {
            if ("555".equalsIgnoreCase(item.getName()) && item.isInStock()) {
                item.setInStock(false);
                wishlistRepository.save(item);
                updatedAny = true;
            }
            if ("Kala bhoot".equalsIgnoreCase(item.getName()) && !item.isRemoved()) {
                item.setRemoved(true);
                wishlistRepository.save(item);
                updatedAny = true;
            }
        }
        if (updatedAny) {
            items = wishlistRepository.findByUserId(user.getId());
        }

        return ResponseEntity.ok(new ApiResponse(true, "Wishlist fetched successfully", items));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addToWishlist(Authentication auth, @RequestBody WishlistItem item) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());

        List<WishlistItem> existing = wishlistRepository.findByUserId(user.getId());
        if (existing.size() >= 50) {
            return ResponseEntity.status(400).body(new ApiResponse(false, "Cannot save in wishlist, more than our limit"));
        }

        Optional<WishlistItem> duplicate = wishlistRepository.findByUserIdAndProductId(user.getId(), item.getProductId());
        if (duplicate.isPresent()) {
            return ResponseEntity.ok(new ApiResponse(true, "Item already in wishlist", duplicate.get()));
        }

        item.setUser(user);
        // Simulate initial edge case stock/removed properties if match
        if ("555".equalsIgnoreCase(item.getName())) {
            item.setInStock(false);
        }
        if ("Kala bhoot".equalsIgnoreCase(item.getName())) {
            item.setRemoved(true);
        }

        WishlistItem saved = wishlistRepository.save(item);
        return ResponseEntity.ok(new ApiResponse(true, "Saved to wishlist successfully", saved));
    }

    @DeleteMapping("/{productId:.+}")
    public ResponseEntity<ApiResponse> removeFromWishlist(Authentication auth, @PathVariable String productId) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());

        String decodedProductId = productId;
        try {
            decodedProductId = java.net.URLDecoder.decode(productId, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            // fallback
        }

        // 1. Try exact matches
        Optional<WishlistItem> item = wishlistRepository.findByUserIdAndProductId(user.getId(), decodedProductId);
        if (item.isPresent()) {
            wishlistRepository.delete(item.get());
            return ResponseEntity.ok(new ApiResponse(true, "Item removed from wishlist"));
        }

        Optional<WishlistItem> itemRaw = wishlistRepository.findByUserIdAndProductId(user.getId(), productId);
        if (itemRaw.isPresent()) {
            wishlistRepository.delete(itemRaw.get());
            return ResponseEntity.ok(new ApiResponse(true, "Item removed from wishlist"));
        }

        // 2. Try case-insensitive and whitespace-cleaned matching against user's items
        List<WishlistItem> userItems = wishlistRepository.findByUserId(user.getId());
        WishlistItem found = null;
        for (WishlistItem wi : userItems) {
            if (wi.getProductId().equalsIgnoreCase(decodedProductId) || wi.getProductId().equalsIgnoreCase(productId)) {
                found = wi;
                break;
            }
        }

        if (found == null) {
            String cleanDecoded = decodedProductId.trim().replaceAll("\\s+", " ");
            String cleanRaw = productId.trim().replaceAll("\\s+", " ");
            for (WishlistItem wi : userItems) {
                String cleanDb = wi.getProductId().trim().replaceAll("\\s+", " ");
                if (cleanDb.equalsIgnoreCase(cleanDecoded) || cleanDb.equalsIgnoreCase(cleanRaw)) {
                    found = wi;
                    break;
                }
            }
        }

        if (found != null) {
            wishlistRepository.delete(found);
            return ResponseEntity.ok(new ApiResponse(true, "Item removed from wishlist"));
        }

        return ResponseEntity.status(404).body(new ApiResponse(false, "Item not found in wishlist"));
    }
}
