package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.Address;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.AddressRepository;
import com.perfume.rasa.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile/addresses")
public class AddressController {

    private final AddressRepository addressRepository;
    private final UserService userService;

    public AddressController(AddressRepository addressRepository, UserService userService) {
        this.addressRepository = addressRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAddresses(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        List<Address> addresses = addressRepository.findByUserIdAndTemporaryOrderAddressFalse(user.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Addresses fetched successfully", addresses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addAddress(Authentication auth, @RequestBody Address address) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        
        List<Address> existing = addressRepository.findByUserIdAndTemporaryOrderAddressFalse(user.getId());
        if (existing.size() >= 5) {
            return ResponseEntity.status(400).body(new ApiResponse(false, "Maximum address limit of 5 reached. Please delete an address before adding a new one."));
        }

        // If it's the first address, make it default
        if (existing.isEmpty()) {
            address.setDefault(true);
        } else if (address.isDefault()) {
            // Unset other defaults
            for (Address a : existing) {
                if (a.isDefault()) {
                    a.setDefault(false);
                    addressRepository.save(a);
                }
            }
        }
        
        address.setUser(user);
        address.setTemporaryOrderAddress(false);
        // Null-safety: areaLocality is NOT NULL in DB
        if (address.getAreaLocality() == null) address.setAreaLocality("");
        if (address.getLabel() == null) address.setLabel("Home");
        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(new ApiResponse(true, "Address saved successfully", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateAddress(Authentication auth, @PathVariable Long id, @RequestBody Address addressDetails) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        Address address = addressRepository.findById(id).orElseThrow(() -> new RuntimeException("Address not found"));
        
        if (!address.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse(false, "Forbidden"));
        }
        
        if (addressDetails.isDefault() && !address.isDefault()) {
            List<Address> existing = addressRepository.findByUserIdAndTemporaryOrderAddressFalse(user.getId());
            for (Address a : existing) {
                if (a.isDefault()) {
                    a.setDefault(false);
                    addressRepository.save(a);
                }
            }
        }

        address.setLabel(addressDetails.getLabel());
        address.setFullName(addressDetails.getFullName());
        address.setAddressLine1(addressDetails.getAddressLine1());
        address.setAreaLocality(addressDetails.getAreaLocality());
        address.setCity(addressDetails.getCity());
        address.setPincode(addressDetails.getPincode());
        address.setDefault(addressDetails.isDefault());
        address.setTemporaryOrderAddress(false);

        Address updated = addressRepository.save(address);
        return ResponseEntity.ok(new ApiResponse(true, "Address updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteAddress(Authentication auth, @PathVariable Long id) {
        if (auth == null) {
            return ResponseEntity.status(401).body(new ApiResponse(false, "Unauthorized"));
        }
        User user = userService.getUserByEmail(auth.getName());
        Address address = addressRepository.findById(id).orElseThrow(() -> new RuntimeException("Address not found"));
        
        if (!address.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new ApiResponse(false, "Forbidden"));
        }
        
        addressRepository.delete(address);
        return ResponseEntity.ok(new ApiResponse(true, "Address deleted"));
    }
}
