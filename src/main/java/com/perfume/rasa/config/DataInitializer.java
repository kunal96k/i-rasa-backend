package com.perfume.rasa.config;

import com.perfume.rasa.model.Coupon;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.CouponRepository;
import com.perfume.rasa.repository.UserRepository;
import com.perfume.rasa.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.admin.email:irasaperfumes@gmail.com}")
    private String adminEmail;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.admin.name:System Admin}")
    private String adminName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public DataInitializer(CouponRepository couponRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JdbcTemplate jdbcTemplate) {
        this.couponRepository = couponRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Run alter column DDL first to prevent data truncation errors
        try {
            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(20)");
            log.info("Altered column users.role to VARCHAR(20) successfully.");
        } catch (Exception e) {
            log.warn("Could not alter column users.role (it may already be VARCHAR(20) or using non-MySQL DB): {}",
                    e.getMessage());
        }

        // ── Ensure default Super Admin exists ──
        String checkEmail = adminEmail.toLowerCase().trim();
        if (userRepository.findByEmail(checkEmail).isEmpty()) {
            User superadmin = new User();
            superadmin.setEmail(checkEmail);
            superadmin.setPassword(passwordEncoder.encode(adminPassword));
            superadmin.setFullName(adminName);
            superadmin.setPhone("9999999999");
            superadmin.setRole(User.Role.SUPERADMIN);
            superadmin.setEmailVerified(true);
            userRepository.save(superadmin);
            log.info("Default Super Admin created: {} / {}", checkEmail, adminPassword);

            // Send email credentials
            try {
                String loginLink = baseUrl + "/login.html";
                emailService.sendWelcomeEmail(checkEmail, adminName, checkEmail, adminPassword, loginLink);
                log.info("Welcome credentials email sent to Super Admin: {}", checkEmail);
            } catch (Exception ex) {
                log.error("Failed to send welcome credentials email to Super Admin: {}", ex.getMessage());
            }
        }

        // ── Remove ALL coupons except REFIL100 ──
        List<Coupon> allCoupons = couponRepository.findAll();
        for (Coupon c : allCoupons) {
            if (!"REFIL100".equalsIgnoreCase(c.getCode())) {
                couponRepository.delete(c);
            }
        }

        // ── Ensure REFIL100 exists and is properly configured ──
        Coupon refillCoupon = couponRepository.findByCode("REFIL100").orElseGet(Coupon::new);
        refillCoupon.setCode("REFIL100");
        refillCoupon.setDiscountAmount(new BigDecimal("100.00"));
        refillCoupon.setMinCartValue(null); // No minimum order required
        refillCoupon.setActive(true);
        refillCoupon.setExpiryDate(LocalDateTime.of(2099, 12, 31, 23, 59, 59));
        refillCoupon.setDescription(
                "Bring your empty bottle to our store to get \u20b9100 off on your next refill. This coupon is noted on your invoice.");
        refillCoupon.setValidity("All Time");
        refillCoupon.setDiscount("\u267b\ufe0f Bottle Refill Offer");

        couponRepository.save(refillCoupon);
    }
}
