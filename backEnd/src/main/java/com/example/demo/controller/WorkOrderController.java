package com.example.demo.controller;

import com.example.demo.dto.InvoiceResponse;
import com.example.demo.model.Invoice;
import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private InvoiceService InvoiceService;


    @PutMapping("/{id}/status")
    public ResponseEntity<WorkOrder> updateStatus(
            @PathVariable Long id,
            @RequestParam Status status
    ) {
        WorkOrder updated = workOrderService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    public ResponseEntity<List<WorkOrder>> getAll() {
        return ResponseEntity.ok(workOrderService.getAll());
    }

    @GetMapping("/Invoice/{productId}")
    public ResponseEntity<WorkOrder> getByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(workOrderService.getByProductId(productId));
    }
    @GetMapping("/late")
    public ResponseEntity<List<WorkOrder>> getLateProducts(){
        List<WorkOrder> lateProducts = workOrderService.getLateProducts();
        return ResponseEntity.ok(lateProducts);
    }

    @GetMapping("/statuses")
    public ResponseEntity<Status []> getAllStatus(){return ResponseEntity.ok(Status.values());
    }
}
