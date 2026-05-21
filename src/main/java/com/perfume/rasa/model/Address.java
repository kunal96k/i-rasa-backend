package com.perfume.rasa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false)
    private String label; // "Home", "Work", "Other"

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String addressLine1;

    @Column(nullable = false)
    private String areaLocality;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String pincode;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "is_temporary_order_address", nullable = false)
    private boolean temporaryOrderAddress = false;

    public Address() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAreaLocality() { return areaLocality; }
    public void setAreaLocality(String areaLocality) { this.areaLocality = areaLocality; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public boolean isTemporaryOrderAddress() { return temporaryOrderAddress; }
    public void setTemporaryOrderAddress(boolean temporaryOrderAddress) { this.temporaryOrderAddress = temporaryOrderAddress; }
}
