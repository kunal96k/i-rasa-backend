package com.perfume.rasa.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.perfume.rasa.model.Order;
import com.perfume.rasa.model.OrderItem;
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

            // Try to load the Samarkan font from the front-end fonts folder (workspace)
            // Relative path from the `rasa` module to the i-rasa frontend fonts directory
            String possibleFontPath = "../i-rasa/fonts/SAMARN__.TTF";
            try {
                java.nio.file.Path fp = java.nio.file.Paths.get(possibleFontPath).toAbsolutePath();
                if (java.nio.file.Files.exists(fp)) {
                    fontProvider.addFont(fp.toString());
                }
            } catch (Exception ex) {
                // ignore and continue; fallback to system fonts
            }

            // Add default fonts as fallback
            fontProvider.addStandardPdfFonts();
            props.setFontProvider(fontProvider);

            // Set a base URI so relative resources (images/css) inside templates resolve
            props.setBaseUri(".");

            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream, props);
        } catch (Exception e) {
            // Fallback to simple conversion if anything fails
            log.warn("Advanced PDF conversion failed, falling back to basic HtmlConverter: {}", e.getMessage());
            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(htmlContent, pdfOutputStream);
        }
        
        log.info("PDF invoice generated successfully for order: {}", order.getOrderId());
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
        context.setVariable("createdAt", new SimpleDateFormat("dd MMM yyyy").format(order.getCreatedAt()));
        context.setVariable("expectedDeliveryDate", 
            order.getExpectedDeliveryDate() != null ? 
            new SimpleDateFormat("dd MMM yyyy").format(order.getExpectedDeliveryDate()) : "N/A");
        context.setVariable("currencySymbol", "$ ");
        context.setVariable("currencyCode", "USD");
        context.setVariable("currencyLabel", "USD");
        context.setVariable("companyEmail", "support@rasaperfumes.in");
        context.setVariable("companyWebsite", "www.irasaperfumes.in");
        
        // Add user info
        context.setVariable("customerName", order.getUser().getFullName());
        context.setVariable("customerEmail", order.getUser().getEmail());
        context.setVariable("customerPhone", order.getUser().getPhone());
        
        // Add addresses
        if (order.getBillingAddress() != null) {
            context.setVariable("billingAddress", formatAddress(order.getBillingAddress()));
        }
        if (order.getShippingAddress() != null) {
            context.setVariable("shippingAddress", formatAddress(order.getShippingAddress()));
        }
        
        // Add items
        context.setVariable("items", order.getItems());
        context.setVariable("itemCount", order.getItems().size());
        
        // Add pricing breakdown
        context.setVariable("subtotal", order.getSubtotal());
        context.setVariable("discount", order.getDiscount());
        context.setVariable("shipping", order.getShipping());
        context.setVariable("handlingCharge", order.getHandlingCharge());
        context.setVariable("platformFee", order.getPlatformFee());
        context.setVariable("total", order.getTotal());
        
        // Add payment info
        context.setVariable("paymentMethod", order.getPaymentMethod());
        context.setVariable("transactionId", order.getTransactionId() != null ? order.getTransactionId() : "N/A");
        
        // Add coupon info if applied
        if (order.getCouponCode() != null && !order.getCouponCode().isEmpty()) {
            context.setVariable("couponCode", order.getCouponCode());
            context.setVariable("discountAmount", order.getDiscount());
        }
        
        // Process template and return HTML
        return templateEngine.process("email/invoice", context);
    }

    /**
     * Format address object into readable string
     */
    private String formatAddress(Object address) {
        if (address == null) return "";
        try {
            // Use reflection to get address fields
            String street = (String) address.getClass().getMethod("getStreet").invoke(address);
            String city = (String) address.getClass().getMethod("getCity").invoke(address);
            String state = (String) address.getClass().getMethod("getState").invoke(address);
            String zipCode = (String) address.getClass().getMethod("getZipCode").invoke(address);
            String country = (String) address.getClass().getMethod("getCountry").invoke(address);
            
            return String.format("%s, %s, %s - %s, %s", street, city, state, zipCode, country);
        } catch (Exception e) {
            log.warn("Error formatting address: {}", e.getMessage());
            return address.toString();
        }
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
        summary.put("customer", order.getUser().getFullName());
        summary.put("customerPhone", order.getUser().getPhone());
        summary.put("email", order.getUser().getEmail());
        summary.put("items", order.getItems());
        summary.put("subtotal", order.getSubtotal());
        summary.put("discount", order.getDiscount());
        summary.put("shipping", order.getShipping());
        summary.put("handlingCharge", order.getHandlingCharge());
        summary.put("platformFee", order.getPlatformFee());
        summary.put("total", order.getTotal());
        summary.put("currencySymbol", "$ ");
        summary.put("currencyLabel", "USD");
        summary.put("paymentMethod", order.getPaymentMethod());
        summary.put("billingAddress", formatAddress(order.getBillingAddress()));
        summary.put("shippingAddress", formatAddress(order.getShippingAddress()));
        
        return summary;
    }
}
