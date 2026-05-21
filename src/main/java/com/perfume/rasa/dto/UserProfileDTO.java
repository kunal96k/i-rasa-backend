package com.perfume.rasa.dto;

import java.time.LocalDate;

public class UserProfileDTO {
    private String fullName;
    private String email;
    private String phoneNumber;
    private LocalDate birthDate;
    private String profileImageUrl;
    private Boolean emailNotificationsEnabled;
    private Boolean smsAlertsEnabled;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public Boolean getEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    public void setEmailNotificationsEnabled(Boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }
    public Boolean getSmsAlertsEnabled() { return smsAlertsEnabled; }
    public void setSmsAlertsEnabled(Boolean smsAlertsEnabled) { this.smsAlertsEnabled = smsAlertsEnabled; }
}
