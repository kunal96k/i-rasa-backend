package com.perfume.rasa.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing input data
 * Prevents data corruption, injection attacks, and invalid states
 */
@Slf4j
@Component
public class DataValidator {

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[0-9+]{10,13}$");
    
    private static final Pattern ALPHANUMERIC_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9\\s\\-.,()]+$");

    private static final int MAX_STRING_LENGTH = 500;
    private static final int MAX_MESSAGE_LENGTH = 5000;
    private static final BigDecimal MIN_PRICE = BigDecimal.ZERO;
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    /**
     * Validate email address
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validate phone number
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim().replaceAll("\\s", "")).matches();
    }

    /**
     * Validate string length
     */
    public static boolean isValidStringLength(String str, int maxLength) {
        if (str == null) {
            return false;
        }
        return str.length() > 0 && str.length() <= maxLength;
    }

    /**
     * Sanitize string input - removes potential XSS/SQL injection
     */
    public static String sanitizeString(String input) {
        if (input == null) {
            return "";
        }
        return input
            .trim()
            .replaceAll("<script>", "")
            .replaceAll("</script>", "")
            .replaceAll("'", "\\'")
            .replaceAll("\"", "\\\"");
    }

    /**
     * Validate price is within acceptable range
     */
    public static boolean isValidPrice(BigDecimal price) {
        if (price == null) {
            return false;
        }
        return price.compareTo(MIN_PRICE) >= 0 && price.compareTo(MAX_PRICE) <= 0;
    }

    /**
     * Validate quantity is positive integer
     */
    public static boolean isValidQuantity(Integer quantity) {
        return quantity != null && quantity > 0 && quantity <= 10000;
    }

    /**
     * Validate order status
     */
    public static boolean isValidOrderStatus(String status) {
        if (status == null) {
            return false;
        }
        return status.matches("^(PENDING|PROCESSING|COMPLETED|CANCELLED|CONFIRMED|SHIPPED|DELIVERED|REFUNDED|EXCHANGED)$");
    }

    /**
     * Validate coupon code format
     */
    public static boolean isValidCouponCode(String code) {
        if (code == null) {
            return false;
        }
        return code.matches("^[A-Z0-9]{3,20}$");
    }

    /**
     * Validate payment method
     */
    public static boolean isValidPaymentMethod(String method) {
        if (method == null) {
            return false;
        }
        return method.matches("^(UPI|CREDIT_CARD|DEBIT_CARD|NET_BANKING|WALLET|COD)$");
    }

    /**
     * Validate that a required field is not empty
     */
    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Validate contact form submission
     */
    public static boolean isValidContactSubmission(String name, String email, String subject, String message) {
        boolean isValid = 
            isNotEmpty(name) && isValidStringLength(name, MAX_STRING_LENGTH) &&
            isValidEmail(email) &&
            isNotEmpty(subject) && isValidStringLength(subject, MAX_STRING_LENGTH) &&
            isNotEmpty(message) && isValidStringLength(message, MAX_MESSAGE_LENGTH);
        
        if (!isValid) {
            log.warn("Invalid contact submission: name={}, email={}, subject={}, messageLength={}",
                name != null ? name.length() : 0,
                email != null ? email.length() : 0,
                subject != null ? subject.length() : 0,
                message != null ? message.length() : 0);
        }
        
        return isValid;
    }

    /**
     * Validate address fields
     */
    public static boolean isValidAddress(String street, String city, String state, String zipCode) {
        return isNotEmpty(street) && isValidStringLength(street, MAX_STRING_LENGTH) &&
               isNotEmpty(city) && isValidStringLength(city, 100) &&
               isNotEmpty(state) && isValidStringLength(state, 100) &&
               isNotEmpty(zipCode) && zipCode.matches("^[0-9]{6}$");
    }

    /**
     * Check for null or empty collection
     */
    public static <T> boolean isNotEmpty(java.util.Collection<T> collection) {
        return collection != null && !collection.isEmpty();
    }
}
