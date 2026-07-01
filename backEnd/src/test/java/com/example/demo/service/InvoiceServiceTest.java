package com.example.demo.service;

import com.example.demo.dto.InvoiceCreateRequest;
import com.example.demo.model.AppUser;
import com.example.demo.model.Invoice;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.Status;
import com.example.demo.model.Tenant;
import com.example.demo.model.WorkOrder;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.PaymentRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import com.example.demo.repository.WorkOrderRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private final InvoiceRepo invoiceRepo = mock(InvoiceRepo.class);
    private final WorkOrderRepo workOrderRepo = mock(WorkOrderRepo.class);
    private final AppUserService userService = mock(AppUserService.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final CustomerRepo customerRepo = mock(CustomerRepo.class);
    private final TenantRepo tenantRepo = mock(TenantRepo.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);

    private final InvoiceService service = new InvoiceService(
            invoiceRepo, workOrderRepo, userService, paymentRepo,
            customerRepo, tenantRepo, userRepo, activityLogService);

    @Test
    void manualCreateDefaultsToEnGestionAndNullPagoStatus() {
        Tenant tenant = tenant();
        AppUser owner = owner(tenant);
        stubRepos(tenant, owner);

        service.createForTenant(minimalRequest(), 1L, 2L);

        ArgumentCaptor<WorkOrder> workOrderCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepo).save(workOrderCaptor.capture());
        assertEquals(Status.EN_GESTION, workOrderCaptor.getValue().getStatus());

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(invoiceCaptor.capture());
        assertNull(invoiceCaptor.getValue().getPagoStatus());
    }

    @Test
    void telegramOverrideCreatesCerradoAndPagado() {
        Tenant tenant = tenant();
        AppUser owner = owner(tenant);
        stubRepos(tenant, owner);

        service.createForTenant(minimalRequest(), 1L, 2L, Status.CERRADO, PaymentStatus.PAGADO);

        ArgumentCaptor<WorkOrder> workOrderCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepo).save(workOrderCaptor.capture());
        assertEquals(Status.CERRADO, workOrderCaptor.getValue().getStatus());

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepo).save(invoiceCaptor.capture());
        assertEquals(PaymentStatus.PAGADO, invoiceCaptor.getValue().getPagoStatus());
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        return tenant;
    }

    private AppUser owner(Tenant tenant) {
        AppUser owner = new AppUser();
        owner.setId(2L);
        owner.setTenant(tenant);
        return owner;
    }

    private void stubRepos(Tenant tenant, AppUser owner) {
        when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepo.findByIdAndTenant_Id(2L, 1L)).thenReturn(Optional.of(owner));
        when(invoiceRepo.save(any())).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(10L);
            return invoice;
        });
        when(workOrderRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private InvoiceCreateRequest minimalRequest() {
        return new InvoiceCreateRequest(
                null, "Test Invoice", 1L, null, null, null,
                null, null, new BigDecimal("100.00"), null,
                null, null, null, List.of());
    }
}
