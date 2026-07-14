package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.CustomerCreateRequest;
import com.example.demo.dto.CustomerResponse;
import com.example.demo.model.AppUser;
import com.example.demo.model.Customer;
import com.example.demo.model.Tenant;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.TenantRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    private final CustomerRepo customerRepo;
    private final TenantRepo tenantRepo;
    private final AppUserService appUserService;
    private final ActivityLogService activityLogService;

    public CustomerService(CustomerRepo customerRepo,
                           TenantRepo tenantRepo,
                           AppUserService appUserService,
                           ActivityLogService activityLogService) {
        this.customerRepo = customerRepo;
        this.tenantRepo = tenantRepo;
        this.appUserService = appUserService;
        this.activityLogService = activityLogService;
    }

    public List<CustomerResponse> findByTenant() {
        return customerRepo.findByTenant_Id(tenantId()).stream().map(CustomerResponse::from).toList();
    }

    public List<CustomerResponse> search(String q) {
        return customerRepo.search(tenantId(), q == null ? "" : q).stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse create(CustomerCreateRequest req) {
        Customer customer = new Customer();
        apply(customer, req);
        customer.setTenant(tenant());
        customer.setCreatedAt(LocalDateTime.now());
        if (customer.getPaymentScore() == null) customer.setPaymentScore(100);
        Customer saved = customerRepo.save(customer);
        AppUser actor = appUserService.getCurrentUser();
        Long actorId = actor != null ? actor.getId() : null;
        activityLogService.record(saved.getTenant().getId(), ActivityLogService.CUSTOMER_CREATED, actorId);
        return CustomerResponse.from(saved);
    }

    public CustomerResponse findOrCreate(CustomerCreateRequest req) {
        Long tenantId = tenantId();
        Optional<Customer> match = Optional.empty();
        if (!isBlank(req.cuitDni())) {
            match = customerRepo.findFirstByTenant_IdAndCuitDniIgnoreCase(tenantId, req.cuitDni().trim());
        }
        if (match.isEmpty() && !isBlank(req.name())) {
            match = customerRepo.findFirstByTenant_IdAndNameIgnoreCase(tenantId, req.name().trim());
        }
        if (match.isEmpty() && !isBlank(req.email())) {
            match = customerRepo.findFirstByTenant_IdAndEmailIgnoreCase(tenantId, req.email().trim());
        }
        if (match.isPresent()) return CustomerResponse.from(match.get());
        if (isBlank(req.name())) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }
        return create(req);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public CustomerResponse update(Long id, CustomerCreateRequest req) {
        Customer customer = customerRepo.findByIdAndTenant_Id(id, tenantId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
        apply(customer, req);
        return CustomerResponse.from(customerRepo.save(customer));
    }

    public void delete(Long id) {
        Customer customer = customerRepo.findByIdAndTenant_Id(id, tenantId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
        customerRepo.delete(customer);
    }

    private void apply(Customer customer, CustomerCreateRequest req) {
        customer.setName(req.name());
        customer.setCuitDni(req.cuitDni());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setNotes(req.notes());
        customer.setPaymentScore(req.paymentScore());
    }

    private Long tenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId != null) return tenantId;
        throw new RuntimeException("Tenant not available");
    }

    private Tenant tenant() {
        return tenantRepo.findById(tenantId())
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
    }
}
