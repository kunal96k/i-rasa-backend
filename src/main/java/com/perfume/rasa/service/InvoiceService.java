package com.perfume.rasa.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.OrderItem;
import com.perfume.rasa.model.Address;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating PDF invoices
 * Handles conversion of HTML templates to PDF format with proper branding
 */
@Slf4j
@Service
public class InvoiceService {

    @Autowired
    private TemplateEngine templateEngine;

    /**
     * Generate PDF invoice from Order object
     * @param order The order to generate invoice for
     * @return ByteArrayOutputStream containing PDF data
     */
    public ByteArrayOutputStream generateInvoicePDF(Order order) {
        // Generate HTML content
        String htmlContent = generateInvoiceHTML(order);
        
        // Convert HTML to PDF with font embedding and proper converter properties
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        try {
            com.itextpdf.html2pdf.ConverterProperties props = new com.itextpdf.html2pdf.ConverterProperties();
            com.itextpdf.layout.font.FontProvider fontProvider = new com.itextpdf.layout.font.FontProvider();

            // Try to load the Samarkan font from possible locations
            String[] possibleFontPaths = {
                "../i-rasa/Samarkan-font/SAMARN__.TTF",
                "../i-rasa/fonts/SAMARN__.TTF",
                "c:/Users/TechnoKraft/Desktop/i-rasa/i-rasa/Samarkan-font/SAMARN__.TTF"
            };
            for (String fontPath : possibleFontPaths) {
                try {
                    java.nio.file.Path fp = java.nio.file.Paths.get(fontPath).toAbsolutePath();
                    if (java.nio.file.Files.exists(fp)) {
                        fontProvider.addFont(fp.toString());
                        log.info("Successfully registered Samarkan font from: {}", fp.toString());
                    }
                } catch (Exception ex) {
                    // ignore and try next
                }
            }

            // Add default fonts as fallback
            fontProvider.addStandardPdfFonts();
            props.setFontProvider(fontProvider);

            // Set a base URI so relative resources (images/css) resolve
            props.setBaseUri(".");

            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream, props);
        } catch (Exception e) {
            // Fallback to simple conversion if anything fails
            log.warn("Advanced PDF conversion failed, falling back to basic HtmlConverter: {}", e.getMessage());
            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream);
        }
        
        log.info("PDF invoice generated successfully for order: {}", order.getId());
        return pdfOutputStream;
    }

    /**
     * Generate HTML invoice content using Thymeleaf template
     * @param order The order object
     * @return HTML string with invoice content
     */
    private String generateInvoiceHTML(Order order) {
        Context context = new Context();
        
        // Add order data to context
        context.setVariable("orderId", order.getId());
        context.setVariable("orderStatus", order.getStatus());
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy");
        java.util.Date orderDate = java.sql.Timestamp.valueOf(order.getCreatedAt());
        context.setVariable("createdAt", sdf.format(orderDate));
        
        context.setVariable("expectedDeliveryDate", 
            order.getExpectedDeliveryDate() != null ? 
            sdf.format(java.sql.Date.valueOf(order.getExpectedDeliveryDate())) : "N/A");
        context.setVariable("currencySymbol", "Rs. ");
        context.setVariable("currencyCode", "INR");
        context.setVariable("currencyLabel", "INR");
        context.setVariable("companyEmail", "support@rasaperfumes.in");
        context.setVariable("companyWebsite", "www.irasaperfumes.in");
        
        // Add user/guest info
        String customerName = "Customer";
        String customerEmail = "N/A";
        String customerPhone = "N/A";
        if (order.getUser() != null) {
            customerName = order.getUser().getFullName() != null ? order.getUser().getFullName() : "Customer";
            customerEmail = order.getUser().getEmail() != null ? order.getUser().getEmail() : "N/A";
            customerPhone = order.getUser().getPhone() != null ? order.getUser().getPhone() : "N/A";
        } else if (order.getBillingAddress() != null) {
            customerName = order.getBillingAddress().getFullName() != null ? order.getBillingAddress().getFullName() : "Customer";
            customerEmail = order.getBillingAddress().getEmail() != null ? order.getBillingAddress().getEmail() : "N/A";
            customerPhone = order.getBillingAddress().getPhone() != null ? order.getBillingAddress().getPhone() : "N/A";
        }
        context.setVariable("customerName", customerName);
        context.setVariable("customerEmail", customerEmail);
        context.setVariable("customerPhone", customerPhone);
        
        // Add addresses
        if (order.getBillingAddress() != null) {
            context.setVariable("billingAddress", formatAddress(order.getBillingAddress()));
        } else {
            context.setVariable("billingAddress", "N/A");
        }
        if (order.getShippingAddress() != null) {
            context.setVariable("shippingAddress", formatAddress(order.getShippingAddress()));
        } else {
            context.setVariable("shippingAddress", "N/A");
        }
        
        // Add items
        context.setVariable("items", order.getItems());
        context.setVariable("itemCount", order.getItems().size());
        
        // Add pricing breakdown
        context.setVariable("subtotal", order.getSubtotal());
        context.setVariable("discount", order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO);
        context.setVariable("shipping", order.getShipping() != null ? order.getShipping() : BigDecimal.ZERO);
        context.setVariable("handlingCharge", order.getHandlingCharge() != null ? order.getHandlingCharge() : BigDecimal.ZERO);
        context.setVariable("platformFee", order.getPlatformFee() != null ? order.getPlatformFee() : BigDecimal.ZERO);
        context.setVariable("platformServicesFee", order.getPlatformServicesFee() != null ? order.getPlatformServicesFee() : BigDecimal.ZERO);
        context.setVariable("total", order.getTotal());
        
        // Add payment info
        context.setVariable("paymentMethod", order.getPaymentMethod());
        context.setVariable("transactionId", order.getTransactionId() != null ? order.getTransactionId() : "N/A");
        
        // Add coupon info if applied
        if (order.getCouponCode() != null && !order.getCouponCode().isEmpty()) {
            context.setVariable("couponCode", order.getCouponCode());
            context.setVariable("discountAmount", order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO);
        }
        
        // Process template and return HTML
        return templateEngine.process("email/invoice", context);
    }

    /**
     * Format address object into readable string
     */
    private String formatAddress(Address address) {
        if (address == null) return "N/A";
        StringBuilder sb = new StringBuilder();
        if (address.getAddressLine1() != null) {
            sb.append(address.getAddressLine1());
        }
        if (address.getAreaLocality() != null && !address.getAreaLocality().trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address.getAreaLocality().trim());
        }
        sb.append("<br/>");
        if (address.getCity() != null && !address.getCity().trim().isEmpty()) {
            sb.append(address.getCity().trim());
        }
        if (address.getPincode() != null && !address.getPincode().trim().isEmpty()) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append(" - ");
            sb.append(address.getPincode().trim());
        }
        sb.append(", India");
        return sb.toString();
    }

    /**
     * Generate invoice summary data (for view modal without PDF)
     */
    public Map<String, Object> getInvoiceSummary(Order order) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("orderId", order.getId());
        summary.put("status", order.getStatus());
        summary.put("createdAt", order.getCreatedAt());
        summary.put("expectedDeliveryDate", order.getExpectedDeliveryDate());
        
        String customerName = "Customer";
        String customerEmail = "N/A";
        String customerPhone = "N/A";
        if (order.getUser() != null) {
            customerName = order.getUser().getFullName() != null ? order.getUser().getFullName() : "Customer";
            customerEmail = order.getUser().getEmail() != null ? order.getUser().getEmail() : "N/A";
            customerPhone = order.getUser().getPhone() != null ? order.getUser().getPhone() : "N/A";
        } else if (order.getBillingAddress() != null) {
            customerName = order.getBillingAddress().getFullName() != null ? order.getBillingAddress().getFullName() : "Customer";
            customerEmail = order.getBillingAddress().getEmail() != null ? order.getBillingAddress().getEmail() : "N/A";
            customerPhone = order.getBillingAddress().getPhone() != null ? order.getBillingAddress().getPhone() : "N/A";
        }
        
        summary.put("customer", customerName);
        summary.put("customerPhone", customerPhone);
        summary.put("email", customerEmail);
        summary.put("items", order.getItems());
        summary.put("subtotal", order.getSubtotal());
        summary.put("discount", order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO);
        summary.put("shipping", order.getShipping() != null ? order.getShipping() : BigDecimal.ZERO);
        summary.put("handlingCharge", order.getHandlingCharge() != null ? order.getHandlingCharge() : BigDecimal.ZERO);
        summary.put("platformFee", order.getPlatformFee() != null ? order.getPlatformFee() : BigDecimal.ZERO);
        summary.put("platformServicesFee", order.getPlatformServicesFee() != null ? order.getPlatformServicesFee() : BigDecimal.ZERO);
        summary.put("total", order.getTotal());
        summary.put("currencySymbol", "₹ ");
        summary.put("currencyLabel", "INR");
        summary.put("paymentMethod", order.getPaymentMethod());
        summary.put("billingAddress", formatAddress(order.getBillingAddress()));
        summary.put("shippingAddress", formatAddress(order.getShippingAddress()));
        
        return summary;
    }

    /**
     * Generate PDF for Usage & Care Guidelines
     * @return ByteArrayOutputStream containing PDF data
     */
    public ByteArrayOutputStream generateGuidelinesPDF() {
        Context context = new Context();
        String htmlContent = templateEngine.process("email/guidelines", context);
        
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        try {
            com.itextpdf.html2pdf.ConverterProperties props = new com.itextpdf.html2pdf.ConverterProperties();
            com.itextpdf.layout.font.FontProvider fontProvider = new com.itextpdf.layout.font.FontProvider();
            fontProvider.addStandardPdfFonts();
            props.setFontProvider(fontProvider);
            props.setBaseUri(".");
            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream, props);
            log.info("Guidelines PDF generated successfully.");
        } catch (Exception e) {
            log.error("Failed to generate Guidelines PDF: {}", e.getMessage());
            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream);
        }
        return pdfOutputStream;
    }
}
