package com.example.demo.service;

import com.example.demo.model.Invoice;
import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import com.example.demo.repository.WorkOrderRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkOrderService {

    private final WorkOrderRepo workOrderRepository;

    public WorkOrderService(WorkOrderRepo workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    public WorkOrder createForProduct(Invoice Invoice) {
        WorkOrder order = new WorkOrder();
        order.setInvoice(Invoice);
        return workOrderRepository.save(order);
    }

    public WorkOrder updateStatus(Long id, Status status) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("WorkOrder not found"));
        workOrder.setStatus(status);
        workOrder.setUpdateAt(LocalDateTime.now());
        return workOrderRepository.save(workOrder);
    }

    public List<WorkOrder> getAll() {
        return workOrderRepository.findAll();
    }

    public WorkOrder getByProductId(Long productId) {
        return workOrderRepository.findByInvoice_Id(productId)
                .orElseThrow(() -> new RuntimeException("WorkOrder not found for Invoice ID " + productId));
    }

    public long countByType(Status type) {
        return workOrderRepository.findByStatus(type).size();
    }

    public long countByTypeForTenant(Status type, Long tenantId) {
        return workOrderRepository.findByTenantIdAndStatus(tenantId, type).size();
    }

    public long countDueBetween(LocalDate today, LocalDate endOfWeek) {
        return workOrderRepository.countFechaEntrega(today, endOfWeek);
    }

    public long countDueBetweenForTenant(Long tenantId, LocalDate today, LocalDate endOfWeek) {
        return workOrderRepository.countFechaEntregaByTenant(tenantId, today, endOfWeek);
    }

    public List<WorkOrder> getLateProducts() {
        return workOrderRepository.findByStatus(Status.EN_DISPUTA);
    }

    public Map<String, Long> countOrdersByStatus() {
        return workOrderRepository.findAll().stream()
                .collect(Collectors.groupingBy(w -> w.getStatus().name(), Collectors.counting()));
    }

    public Map<String, Long> countOrdersByStatusForTenant(Long tenantId) {
        return workOrderRepository.countOrdersByStatusForTenant(tenantId).stream()
                .collect(Collectors.toMap(row -> ((Status) row[0]).name(), row -> (Long) row[1]));
    }
}
