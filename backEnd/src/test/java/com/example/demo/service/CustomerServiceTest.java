package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.CustomerCreateRequest;
import com.example.demo.dto.CustomerResponse;
import com.example.demo.model.AppUser;
import com.example.demo.model.Customer;
import com.example.demo.model.Tenant;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.TenantRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private final CustomerRepo customerRepo = mock(CustomerRepo.class);
    private final TenantRepo tenantRepo = mock(TenantRepo.class);
    private final AppUserService appUserService = mock(AppUserService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);

    private final CustomerService service = new CustomerService(
            customerRepo, tenantRepo, appUserService, activityLogService);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createWithNoMatchInsertsNewCustomer() {
        Tenant tenant = tenant();
        AppUser actor = actor(tenant);
        TenantContext.set(1L);
        when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));
        when(appUserService.getCurrentUser()).thenReturn(actor);
        when(customerRepo.findFirstByTenant_IdAndCuitDniIgnoreCase(1L, "20-12345678-9"))
                .thenReturn(Optional.empty());
        when(customerRepo.findFirstByTenant_IdAndNameIgnoreCase(1L, "Acme SA"))
                .thenReturn(Optional.empty());
        when(customerRepo.save(any())).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(10L);
            return customer;
        });

        CustomerResponse response = service.create(new CustomerCreateRequest(
                "Acme SA", "20-12345678-9", null, "+54 11 1234-5678", null, null));

        assertEquals(10L, response.id());
        assertEquals("Acme SA", response.name());
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepo).save(customerCaptor.capture());
        assertEquals(tenant, customerCaptor.getValue().getTenant());
        assertEquals(100, customerCaptor.getValue().getPaymentScore());
        verify(activityLogService).record(1L, ActivityLogService.CUSTOMER_CREATED, 2L);
    }

    @Test
    void createWithMatchingCuitDniReturnsExistingCustomerWithoutInsert() {
        Tenant tenant = tenant();
        Customer existing = customer(tenant);
        TenantContext.set(1L);
        when(customerRepo.findFirstByTenant_IdAndCuitDniIgnoreCase(1L, "20-12345678-9"))
                .thenReturn(Optional.of(existing));

        CustomerResponse response = service.create(new CustomerCreateRequest(
                "Acme SA", "20-12345678-9", null, null, null, null));

        assertEquals(7L, response.id());
        assertEquals("Acme SA", response.name());
        verify(customerRepo, never()).save(any());
        verify(tenantRepo, never()).findById(any());
        verify(activityLogService, never()).record(any(), any(), any());
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        return tenant;
    }

    private AppUser actor(Tenant tenant) {
        AppUser actor = new AppUser();
        actor.setId(2L);
        actor.setTenant(tenant);
        return actor;
    }

    private Customer customer(Tenant tenant) {
        Customer customer = new Customer();
        customer.setId(7L);
        customer.setTenant(tenant);
        customer.setName("Acme SA");
        customer.setCuitDni("20-12345678-9");
        return customer;
    }
}
