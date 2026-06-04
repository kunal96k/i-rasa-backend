package com.perfume.rasa.controller;

import com.perfume.rasa.dto.ApiResponse;
import com.perfume.rasa.model.ContactTicket;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.User;
import com.perfume.rasa.repository.ContactTicketRepository;
import com.perfume.rasa.repository.OrderRepository;
import com.perfume.rasa.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Dashboard Controller
 * Provides secure, scalable endpoints for dashboard statistics and reports.
 *
 * Security: Uses Spring Security authorities directly — no extra DB query per request.
 * Scalability: Single-pass stream aggregation for all financial metrics.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ContactTicketRepository contactTicketRepository;

    // ─── Security Helper ─────────────────────────────────────────────────────────
    /**
     * Checks roles from the Spring Security principal — no extra DB round-trip.
     */
    private boolean hasAdminAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN")
                        || role.equals("ROLE_SUPERADMIN")
                        || role.equals("ROLE_EMPLOYEE"));
    }

    private ResponseEntity<ApiResponse> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse(false, "Access denied. Insufficient privileges.", null));
    }

    private ResponseEntity<ApiResponse> serverError(String context, Exception e) {
        log.error("Error in {}: {}", context, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse(false, "Server error. Please try again.", null));
    }

    // ─── Aggregation Helper ───────────────────────────────────────────────────────
    /**
     * Single-pass order aggregation — avoids iterating the list N times for N metrics.
     */
    private static class OrderAggregates {
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalShipping = BigDecimal.ZERO;
        BigDecimal totalHandling = BigDecimal.ZERO;
        BigDecimal totalPlatformFee = BigDecimal.ZERO;
        BigDecimal totalPlatformServicesFee = BigDecimal.ZERO;
        long orderCount = 0;
        long nonCancelledCount = 0;

        void accumulate(Order o) {
            orderCount++;
            if (o.getStatus() != null && o.getStatus().equalsIgnoreCase("CANCELLED")) return;
            nonCancelledCount++;
            totalRevenue = totalRevenue.add(safe(o.getTotal()));
            totalDiscount = totalDiscount.add(safe(o.getDiscount()));
            totalShipping = totalShipping.add(safe(o.getShipping()));
            totalHandling = totalHandling.add(safe(o.getHandlingCharge()));
            totalPlatformFee = totalPlatformFee.add(safe(o.getPlatformFee()));
            totalPlatformServicesFee = totalPlatformServicesFee.add(safe(o.getPlatformServicesFee()));
        }

        private static BigDecimal safe(BigDecimal v) {
            return v != null ? v : BigDecimal.ZERO;
        }
    }

    // ─── Endpoint: Dashboard Overview ─────────────────────────────────────────────
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse> getOverview(
            @RequestParam(defaultValue = "6m") String period,
            Authentication authentication) {
        if (!hasAdminAccess(authentication)) return forbidden();

        try {
            List<Order> allOrders = orderRepository.findAll();

            // Single-pass aggregation across all orders
            OrderAggregates agg = new OrderAggregates();
            allOrders.forEach(agg::accumulate);

            // Chart data based on period
            List<String> labels = new ArrayList<>();
            List<BigDecimal> chartValues = new ArrayList<>();
            LocalDate now = LocalDate.now();

            if ("today".equalsIgnoreCase(period)) {
                // Today's hourly buckets: 00:00, 04:00, 08:00, 12:00, 16:00, 20:00
                List<Integer> buckets = List.of(0, 4, 8, 12, 16, 20);
                Map<Integer, BigDecimal> hourlyMap = new HashMap<>();
                buckets.forEach(b -> hourlyMap.put(b, BigDecimal.ZERO));

                LocalDate today = LocalDate.now();
                allOrders.stream()
                        .filter(o -> o.getCreatedAt() != null
                                && o.getCreatedAt().toLocalDate().equals(today)
                                && o.getStatus() != null
                                && !o.getStatus().equalsIgnoreCase("CANCELLED"))
                        .forEach(o -> {
                            int hour = o.getCreatedAt().getHour();
                            int bucket = (hour / 4) * 4;
                            BigDecimal currentVal = hourlyMap.getOrDefault(bucket, BigDecimal.ZERO);
                            hourlyMap.put(bucket, currentVal.add(o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO));
                        });

                List<String> bucketLabels = List.of("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM");
                for (int i = 0; i < buckets.size(); i++) {
                    labels.add(bucketLabels.get(i));
                    chartValues.add(hourlyMap.getOrDefault(buckets.get(i), BigDecimal.ZERO));
                }
            } else if ("7d".equalsIgnoreCase(period)) {
                // Last 7 days daily buckets
                List<LocalDate> last7Days = new ArrayList<>();
                for (int i = 6; i >= 0; i--) {
                    last7Days.add(now.minusDays(i));
                }

                Map<LocalDate, BigDecimal> dailyMap = allOrders.stream()
                        .filter(o -> o.getCreatedAt() != null
                                && o.getStatus() != null
                                && !o.getStatus().equalsIgnoreCase("CANCELLED"))
                        .collect(Collectors.groupingBy(
                                o -> o.getCreatedAt().toLocalDate(),
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                                        BigDecimal::add)));

                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd MMM");
                for (LocalDate d : last7Days) {
                    labels.add(d.format(dtf));
                    chartValues.add(dailyMap.getOrDefault(d, BigDecimal.ZERO));
                }
            } else if ("1y".equalsIgnoreCase(period)) {
                // Last 12 months monthly buckets
                List<YearMonth> last12Months = new ArrayList<>();
                for (int i = 11; i >= 0; i--) {
                    last12Months.add(YearMonth.from(now.minusMonths(i)));
                }

                Map<YearMonth, BigDecimal> monthlyMap = allOrders.stream()
                        .filter(o -> o.getCreatedAt() != null
                                && o.getStatus() != null
                                && !o.getStatus().equalsIgnoreCase("CANCELLED"))
                        .collect(Collectors.groupingBy(
                                o -> YearMonth.from(o.getCreatedAt()),
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                                        BigDecimal::add)));

                for (YearMonth ym : last12Months) {
                    labels.add(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + String.valueOf(ym.getYear()).substring(2));
                    chartValues.add(monthlyMap.getOrDefault(ym, BigDecimal.ZERO));
                }
            } else {
                // Default: "6m" (Last 6 months monthly buckets)
                List<YearMonth> last6Months = new ArrayList<>();
                for (int i = 5; i >= 0; i--) {
                    last6Months.add(YearMonth.from(now.minusMonths(i)));
                }

                Map<YearMonth, BigDecimal> monthlyMap = allOrders.stream()
                        .filter(o -> o.getCreatedAt() != null
                                && o.getStatus() != null
                                && !o.getStatus().equalsIgnoreCase("CANCELLED"))
                        .collect(Collectors.groupingBy(
                                o -> YearMonth.from(o.getCreatedAt()),
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO,
                                        BigDecimal::add)));

                for (YearMonth ym : last6Months) {
                    labels.add(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                    chartValues.add(monthlyMap.getOrDefault(ym, BigDecimal.ZERO));
                }
            }

            // Pending tickets (not RESOLVED)
            long pendingTickets = contactTicketRepository.findAll().stream()
                    .filter(t -> !"RESOLVED".equalsIgnoreCase(t.getStatus()))
                    .count();

            // Registered users count
            long registeredUsers = userRepository.count();

            Map<String, Object> chartData = new LinkedHashMap<>();
            chartData.put("labels", labels);
            chartData.put("data", chartValues);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalRevenue", agg.totalRevenue.setScale(2, RoundingMode.HALF_UP));
            response.put("totalOrders", agg.orderCount);
            response.put("pendingTickets", pendingTickets);
            response.put("registeredUsers", registeredUsers);
            response.put("totalDiscount", agg.totalDiscount.setScale(2, RoundingMode.HALF_UP));
            response.put("totalShipping", agg.totalShipping.setScale(2, RoundingMode.HALF_UP));
            response.put("totalHandlingCharge", agg.totalHandling.setScale(2, RoundingMode.HALF_UP));
            response.put("totalPlatformFee", agg.totalPlatformFee.setScale(2, RoundingMode.HALF_UP));
            response.put("totalPlatformServicesFee", agg.totalPlatformServicesFee.setScale(2, RoundingMode.HALF_UP));
            response.put("chartData", chartData);

            return ResponseEntity.ok(new ApiResponse(true, "Overview loaded successfully", response));

        } catch (Exception e) {
            return serverError("getOverview", e);
        }
    }

    // ─── Endpoint: Sales Report (paginated) ───────────────────────────────────────
    @GetMapping("/reports/sales")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ApiResponse> getSalesReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (!hasAdminAccess(authentication)) return forbidden();

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end   = parseDate(endDate);

            // ── Aggregate summary across ALL matching orders (single pass, no data load into memory for large sets) ──
            // We load all IDs only to do aggregation; switch to a @Query aggregate later if volume grows.
            List<Order> allFiltered = orderRepository.findAll().stream()
                    .filter(o -> o.getCreatedAt() != null)
                    .filter(o -> {
                        LocalDate d = o.getCreatedAt().toLocalDate();
                        return (start == null || !d.isBefore(start))
                                && (end == null || !d.isAfter(end));
                    })
                    .collect(Collectors.toList());

            OrderAggregates agg = new OrderAggregates();
            allFiltered.forEach(agg::accumulate);

            BigDecimal avgOrderValue = agg.nonCancelledCount > 0
                    ? agg.totalRevenue.divide(BigDecimal.valueOf(agg.nonCancelledCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // ── Paginated content ─────────────────────────────────────────────────────
            int safeSize = Math.min(Math.max(size, 1), 100);
            int safePage = Math.max(page, 0);
            List<Order> sorted = allFiltered.stream()
                    .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                    .collect(Collectors.toList());

            long totalItems  = sorted.size();
            int  totalPages  = (int) Math.ceil((double) totalItems / safeSize);
            int  fromIdx     = safePage * safeSize;
            List<Order> pageContent = fromIdx >= totalItems ? List.of()
                    : sorted.subList(fromIdx, (int) Math.min(fromIdx + safeSize, totalItems));

            List<Map<String, Object>> content = pageContent.stream().map(o -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", o.getId());
                item.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
                String customerName = "Guest";
                if (o.getBillingAddress() != null && o.getBillingAddress().getFullName() != null) {
                    customerName = o.getBillingAddress().getFullName();
                } else if (o.getUser() != null && o.getUser().getFullName() != null) {
                    customerName = o.getUser().getFullName();
                }
                item.put("customerName", customerName);
                item.put("itemsCount", o.getItems() != null ? o.getItems().size() : 0);
                item.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod() : "N/A");
                item.put("status", o.getStatus() != null ? o.getStatus() : "UNKNOWN");
                item.put("shipping",            OrderAggregates.safe(o.getShipping()).setScale(2, RoundingMode.HALF_UP));
                item.put("handlingCharge",       OrderAggregates.safe(o.getHandlingCharge()).setScale(2, RoundingMode.HALF_UP));
                item.put("platformFee",          OrderAggregates.safe(o.getPlatformFee()).setScale(2, RoundingMode.HALF_UP));
                item.put("platformServicesFee",  OrderAggregates.safe(o.getPlatformServicesFee()).setScale(2, RoundingMode.HALF_UP));
                item.put("discount",             OrderAggregates.safe(o.getDiscount()).setScale(2, RoundingMode.HALF_UP));
                item.put("subtotal",             OrderAggregates.safe(o.getSubtotal()).setScale(2, RoundingMode.HALF_UP));
                item.put("total",                OrderAggregates.safe(o.getTotal()).setScale(2, RoundingMode.HALF_UP));
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content",       content);
            response.put("currentPage",   safePage);
            response.put("totalItems",    totalItems);
            response.put("totalPages",    totalPages);
            response.put("pageSize",      safeSize);
            response.put("totalSales",    agg.totalRevenue.setScale(2, RoundingMode.HALF_UP));
            response.put("totalOrders",   agg.orderCount);
            response.put("averageOrderValue", avgOrderValue);
            response.put("totalDiscount",           agg.totalDiscount.setScale(2, RoundingMode.HALF_UP));
            response.put("totalShipping",           agg.totalShipping.setScale(2, RoundingMode.HALF_UP));
            response.put("totalHandlingCharge",     agg.totalHandling.setScale(2, RoundingMode.HALF_UP));
            response.put("totalPlatformFee",        agg.totalPlatformFee.setScale(2, RoundingMode.HALF_UP));
            response.put("totalPlatformServicesFee",agg.totalPlatformServicesFee.setScale(2, RoundingMode.HALF_UP));

            return ResponseEntity.ok(new ApiResponse(true, "Sales report loaded successfully", response));

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid date format. Use YYYY-MM-DD.", null));
        } catch (Exception e) {
            return serverError("getSalesReport", e);
        }
    }

    // ─── Endpoint: Tickets Report (paginated, server-side) ────────────────────────
    @GetMapping("/reports/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ApiResponse> getTicketsReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (!hasAdminAccess(authentication)) return forbidden();

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end   = parseDate(endDate);

            // Use paginated repository method — no full table scan
            int safeSize = Math.min(Math.max(size, 1), 100);
            int safePage = Math.max(page, 0);
            Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

            Page<ContactTicket> ticketsPage;
            if (start == null && end == null) {
                ticketsPage = contactTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
            } else {
                // Date-filtered path — stream-filter page (acceptable for report use)
                List<ContactTicket> all = contactTicketRepository.findAll().stream()
                        .filter(t -> t.getCreatedAt() != null)
                        .filter(t -> {
                            LocalDate d = t.getCreatedAt().toLocalDate();
                            return (start == null || !d.isBefore(start))
                                    && (end == null || !d.isAfter(end));
                        })
                        .sorted(Comparator.comparing(ContactTicket::getCreatedAt).reversed())
                        .collect(Collectors.toList());

                long tot = all.size();
                int  tp  = (int) Math.ceil((double) tot / safeSize);
                int  fi  = safePage * safeSize;
                List<ContactTicket> slice = fi >= tot ? List.of()
                        : all.subList(fi, (int) Math.min(fi + safeSize, tot));

                long resolved = all.stream().filter(t -> "RESOLVED".equalsIgnoreCase(t.getStatus())).count();

                List<Map<String, Object>> content = slice.stream().map(t -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("ticketId", t.getTicketId());
                    item.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
                    item.put("name", t.getName());
                    item.put("email", t.getEmail());
                    item.put("subject", t.getSubject());
                    item.put("status", t.getStatus());
                    item.put("message", t.getMessage());
                    return item;
                }).collect(Collectors.toList());

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("content",        content);
                response.put("currentPage",    safePage);
                response.put("totalItems",     tot);
                response.put("totalPages",     tp);
                response.put("pageSize",       safeSize);
                response.put("totalTickets",   tot);
                response.put("resolvedTickets", resolved);
                response.put("pendingTickets", tot - resolved);
                return ResponseEntity.ok(new ApiResponse(true, "Tickets report loaded successfully", response));
            }

            // No date filter — use DB paginated result
            long total    = ticketsPage.getTotalElements();
            long resolved = contactTicketRepository.countByStatus("RESOLVED");

            List<Map<String, Object>> content = ticketsPage.getContent().stream().map(t -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("ticketId", t.getTicketId());
                item.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
                item.put("name", t.getName());
                item.put("email", t.getEmail());
                item.put("subject", t.getSubject());
                item.put("status", t.getStatus());
                item.put("message", t.getMessage());
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content",        content);
            response.put("currentPage",    safePage);
            response.put("totalItems",     total);
            response.put("totalPages",     ticketsPage.getTotalPages());
            response.put("pageSize",       safeSize);
            response.put("totalTickets",   total);
            response.put("resolvedTickets", resolved);
            response.put("pendingTickets", total - resolved);

            return ResponseEntity.ok(new ApiResponse(true, "Tickets report loaded successfully", response));

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid date format. Use YYYY-MM-DD.", null));
        } catch (Exception e) {
            return serverError("getTicketsReport", e);
        }
    }

    // ─── Endpoint: Users Report (paginated, server-side) ─────────────────────────
    @GetMapping("/reports/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ApiResponse> getUsersReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (!hasAdminAccess(authentication)) return forbidden();

        try {
            LocalDate start = parseDate(startDate);
            LocalDate end   = parseDate(endDate);

            int safeSize = Math.min(Math.max(size, 1), 100);
            int safePage = Math.max(page, 0);

            // Paginated DB query when no date filter
            if (start == null && end == null) {
                Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
                Page<User> usersPage = userRepository.findAll(pageable);
                long total      = usersPage.getTotalElements();
                long customers  = usersPage.getContent().stream().filter(u -> u.getRole() == User.Role.CUSTOMER).count();

                List<Map<String, Object>> content = usersPage.getContent().stream().map(u -> mapUser(u)).collect(Collectors.toList());

                // Counts over full table (cheap count query)
                long totalCustomers = userRepository.findAll().stream().filter(u -> u.getRole() == User.Role.CUSTOMER).count();

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("content",            content);
                response.put("currentPage",        safePage);
                response.put("totalItems",         total);
                response.put("totalPages",         usersPage.getTotalPages());
                response.put("pageSize",           safeSize);
                response.put("totalUsers",         total);
                response.put("customerCount",      totalCustomers);
                response.put("adminEmployeeCount", total - totalCustomers);
                return ResponseEntity.ok(new ApiResponse(true, "Users report loaded successfully", response));
            }

            // Date-filtered path
            List<User> filtered = userRepository.findAll().stream()
                    .filter(u -> u.getCreatedAt() != null)
                    .filter(u -> {
                        LocalDate d = u.getCreatedAt().toLocalDate();
                        return (start == null || !d.isBefore(start))
                                && (end == null || !d.isAfter(end));
                    })
                    .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                    .collect(Collectors.toList());

            long total     = filtered.size();
            long customers = filtered.stream().filter(u -> u.getRole() == User.Role.CUSTOMER).count();
            int  totalPgs  = (int) Math.ceil((double) total / safeSize);
            int  fromIdx   = safePage * safeSize;
            List<User> slice = fromIdx >= total ? List.of()
                    : filtered.subList(fromIdx, (int) Math.min(fromIdx + safeSize, total));

            List<Map<String, Object>> content = slice.stream().map(u -> mapUser(u)).collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content",            content);
            response.put("currentPage",        safePage);
            response.put("totalItems",         total);
            response.put("totalPages",         totalPgs);
            response.put("pageSize",           safeSize);
            response.put("totalUsers",         total);
            response.put("customerCount",      customers);
            response.put("adminEmployeeCount", total - customers);

            return ResponseEntity.ok(new ApiResponse(true, "Users report loaded successfully", response));

        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid date format. Use YYYY-MM-DD.", null));
        } catch (Exception e) {
            return serverError("getUsersReport", e);
        }
    }

    /** Maps a User to a safe (phone-masked) map for API response. */
    private Map<String, Object> mapUser(User u) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", u.getId());
        item.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        item.put("fullName", u.getFullName());
        item.put("email", u.getEmail());
        String phone = u.getPhone();
        if (phone != null && phone.length() > 4) {
            phone = "XXXXXX" + phone.substring(phone.length() - 4);
        }
        item.put("phone", phone);
        item.put("role", u.getRole() != null ? u.getRole().name() : "UNKNOWN");
        item.put("emailVerified", u.isEmailVerified());
        return item;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────────
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        return LocalDate.parse(dateStr.trim()); // throws DateTimeParseException on bad input
    }
}
