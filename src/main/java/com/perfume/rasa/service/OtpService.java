package com.perfume.rasa.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

    public void storeOtp(String email, String otp) {
        otpStorage.put(email.toLowerCase(), new OtpData(otp, LocalDateTime.now().plusMinutes(10)));
    }

    public boolean verifyOtp(String email, String otp) {
        String key = email.toLowerCase();
        OtpData data = otpStorage.get(key);

        if (data == null) return false;
        if (data.expiry.isBefore(LocalDateTime.now())) {
            otpStorage.remove(key);
            return false;
        }

        boolean matches = data.otp.equals(otp);
        if (matches) {
            otpStorage.remove(key);
            verifiedEmails.put(key, LocalDateTime.now().plusMinutes(30)); // Verified for 30 mins
        }
        return matches;
    }

    public boolean isEmailVerified(String email) {
        String key = email.toLowerCase();
        LocalDateTime expiry = verifiedEmails.get(key);
        if (expiry == null) return false;
        if (expiry.isBefore(LocalDateTime.now())) {
            verifiedEmails.remove(key);
            return false;
        }
        return true;
    }

    public void clearVerification(String email) {
        verifiedEmails.remove(email.toLowerCase());
    }

    private final Map<String, LocalDateTime> verifiedEmails = new ConcurrentHashMap<>();

    private static class OtpData {
        String otp;
        LocalDateTime expiry;

        OtpData(String otp, LocalDateTime expiry) {
            this.otp = otp;
            this.expiry = expiry;
        }
    }
}
