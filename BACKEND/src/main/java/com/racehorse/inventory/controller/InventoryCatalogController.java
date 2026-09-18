package com.racehorse.inventory.controller;

import com.racehorse.inventory.dto.CreateInventoryCatalogRequest;
import com.racehorse.inventory.dto.InventoryCatalogResponse;
import com.racehorse.inventory.service.InventoryCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/inventory-catalogs")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class InventoryCatalogController {

    private final InventoryCatalogService service;

    public InventoryCatalogController(InventoryCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<InventoryCatalogResponse> getAll(
            @RequestParam(required = false) String type) {
        return service.getAll(type);
    }

    @PostMapping
    public ResponseEntity<InventoryCatalogResponse> create(
            @Valid @RequestBody CreateInventoryCatalogRequest request) {
        InventoryCatalogResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/admin/inventory-catalogs/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public InventoryCatalogResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateInventoryCatalogRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public InventoryCatalogResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PatchMapping("/{id}/reactivate")
    public InventoryCatalogResponse reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }
}
