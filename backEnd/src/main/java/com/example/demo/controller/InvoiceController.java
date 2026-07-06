package com.example.demo.controller;

import com.example.demo.dto.InvoiceCreateRequest;
import com.example.demo.dto.InvoiceResponse;
import com.example.demo.dto.InvoiceUpdateDto;
import com.example.demo.exceptions.ResourceNotFoundException;
import com.example.demo.model.Invoice;
import com.example.demo.service.AppUserService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.InvoiceService;
import com.example.demo.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService InvoiceService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AppUserService userService;

    @Autowired
    private WorkOrderService workOrderService;


    @GetMapping
    public ResponseEntity<Page<InvoiceResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate"));
        Page<InvoiceResponse> products = InvoiceService.getAll(pageable);
        return ResponseEntity.ok(products);
    }


    @GetMapping("/search")
    public ResponseEntity<Page<InvoiceResponse>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate"));
        return ResponseEntity.ok(InvoiceService.searchByTitle(q, pageable));
    }

    @PostMapping("/create")
    public ResponseEntity<InvoiceResponse> createProduct(
            @RequestBody InvoiceCreateRequest req
    ) {
        InvoiceResponse p = InvoiceService.createProduct(req);
        return ResponseEntity.ok(p);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id) throws ResourceNotFoundException {
        InvoiceResponse Invoice = InvoiceService.getById(id);
        if (Invoice == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Invoice);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody InvoiceUpdateDto dto
    ) throws ResourceNotFoundException {
        InvoiceResponse updated = InvoiceService.update(id, dto);
        return ResponseEntity.ok(updated);
    }


    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!Set.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se permiten jpg, jpeg, png, webp"));
        }
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de contenido no permitido"));
        }
        try {
            String url = fileStorageService.saveFile(file);
            InvoiceUpdateDto dto = new InvoiceUpdateDto();
            dto.setFoto(url);
            return ResponseEntity.ok(InvoiceService.update(id, dto));
        } catch (IOException | ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar imagen"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) throws ResourceNotFoundException {
        if (InvoiceService.delete(id)) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/due-this-week")
    public ResponseEntity<List<InvoiceResponse>> getProductsDueThisWeek() {
       List<InvoiceResponse> productsDueThisWeek = InvoiceService.getProductsDueThisWeek();
        return ResponseEntity.ok(productsDueThisWeek);
    }

    @PostMapping("/add-existing")
    public ResponseEntity<Invoice> addExistingOrder(@RequestBody Map<String, String> request) {
        String titulo = request.get("titulo");
        Invoice Invoice = InvoiceService.findByTitle(titulo);

        Invoice.setFechaEstimada(LocalDate.now().plusDays(3)); // middle of this week
        InvoiceService.guardar(Invoice);

        return ResponseEntity.ok(Invoice);
    }


    @GetMapping("/past-due")
    public ResponseEntity<List<InvoiceResponse>> getProductsPastDue() {
        List<InvoiceResponse> productsDueThisWeek = InvoiceService.getProductsPastDue();
        return ResponseEntity.ok(productsDueThisWeek);
    }
    @GetMapping("/not-picked-up")
    public ResponseEntity<List<InvoiceResponse>> getProductsNotPickedUp() {
        List<InvoiceResponse> productsDueThisWeek = InvoiceService.getProductsNotPickedUp();
        return ResponseEntity.ok(productsDueThisWeek);
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<InvoiceResponse>> filterProducts(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String workOrderStatus,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate"));
        return ResponseEntity.ok(InvoiceService.searchWithFilters(
                titulo, workOrderStatus, from, to, pageable));
    }


}
