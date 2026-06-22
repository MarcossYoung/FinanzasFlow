package com.example.demo.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.demo.config.TenantContext;
import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.ProductPayments;
import com.example.demo.model.OrderPayments;
import com.example.demo.model.Invoice;
import com.example.demo.repository.PaymentRepo;
import com.example.demo.repository.InvoiceRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepo orderPaymentsRepo;
    private final InvoiceRepo InvoiceRepo;

    @Autowired(required = false)
    private Cloudinary cloudinary;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    public PaymentService(InvoiceRepo InvoiceRepo, PaymentRepo orderPaymentsRepo) {
        this.orderPaymentsRepo = orderPaymentsRepo;
        this.InvoiceRepo = InvoiceRepo;
    }

    public List<ProductPayments> getPayments(Long id) {
        Long tenantId = currentTenantId();
        InvoiceRepo.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return orderPaymentsRepo.findByInvoice_IdAndTenant_Id(id, tenantId)
                .stream()
                .map(ProductPayments::from)
                .toList();
    }

    public OrderPayments createPayment(CreatePaymentRequest req) {
        log.info("createPayment called with product_id={}, type={}, valor={}, method={}, fecha={}",
                req.product_id(), req.type(), req.valor(), req.paymentMethod(), req.fecha());
        Long tenantId = currentTenantId();
        Invoice Invoice = InvoiceRepo.findByIdAndTenant_Id(req.product_id(), tenantId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        OrderPayments payment = new OrderPayments();
        payment.setAmount(req.valor());
        payment.setPaymentType(req.type());
        payment.setPaymentDate(LocalDate.parse(req.fecha().replace('/', '-')));
        payment.setPaymentMethod(req.paymentMethod());
        payment.setInvoice(Invoice);
        payment.setTenant(Invoice.getTenant());

        return orderPaymentsRepo.save(payment);
    }

    public void uploadReceipt(Long paymentId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is missing");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not allowed. Accepted: jpg, jpeg, png, pdf");
        }
        if (!isAllowedContentType(file.getContentType())) {
            throw new IllegalArgumentException("File content type not allowed. Accepted: jpg, jpeg, png, pdf");
        }

        if (cloudinary == null) throw new IllegalStateException("Cloudinary not configured (check env vars)");

        OrderPayments payment = orderPaymentsRepo.findByIdAndTenant_Id(paymentId, currentTenantId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap("folder", "muebles/comprobantes", "resource_type", "auto")
        );
        String secureUrl = (String) uploadResult.get("secure_url");

        payment.setReceiptPath(secureUrl);
        orderPaymentsRepo.save(payment);
    }

    public String getReceiptUrl(Long paymentId) {
        OrderPayments payment = orderPaymentsRepo.findByIdAndTenant_Id(paymentId, currentTenantId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (payment.getReceiptPath() == null) {
            throw new RuntimeException("No receipt found for this payment");
        }
        return payment.getReceiptPath();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new RuntimeException("Tenant not available");
        }
        return tenantId;
    }

    private boolean isAllowedContentType(String contentType) {
        return contentType != null && Set.of("image/jpeg", "image/png", "application/pdf").contains(contentType);
    }
}
