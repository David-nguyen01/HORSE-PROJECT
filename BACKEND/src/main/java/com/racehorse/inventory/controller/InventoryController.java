package com.racehorse.inventory.controller;

import com.racehorse.inventory.dto.*;
import com.racehorse.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // ==================== ITEMS ====================

    @GetMapping("/items")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')")
    public List<InventoryItemResponse> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) String status) {
        return service.listItems(category, lowStockOnly, status);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')")
    public InventoryItemResponse getItem(@PathVariable UUID id) {
        return service.getItem(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public InventoryItemResponse createItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return service.createItem(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public InventoryItemResponse updateItem(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return service.updateItem(id, UUID.fromString(jwt.getSubject()), request);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public void deleteItem(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        service.deleteItem(id, UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/items/{id}/stock")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public InventoryItemResponse updateStock(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStockRequest request) {
        return service.updateStock(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/items/{id}/usage")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public InventoryUsageResponse recordUsage(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody RecordInventoryUsageRequest request) {
        return service.recordUsage(id, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW') or hasRole('CLUB_MANAGER')")
    public List<InventoryUsageResponse> listUsage(@RequestParam(defaultValue = "20") int limit) {
        return service.listUsage(limit);
    }

    // ==================== REPLENISHMENTS ====================

    @GetMapping("/replenishments")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')")
    public List<ReplenishmentResponse> listReplenishments(
            @RequestParam(required = false) String status) {
        return service.listReplenishments(status);
    }

    @GetMapping("/replenishments/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')")
    public ReplenishmentResponse getReplenishment(@PathVariable UUID id) {
        return service.getReplenishment(id);
    }

    @PostMapping("/replenishments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public ReplenishmentResponse createReplenishment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReplenishmentRequest request) {
        return service.createReplenishment(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/replenishments/{id}/review")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_APPROVE','PERM_INVENTORY_EDIT','PERM_STABLE_CARE_APPROVE','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public ReplenishmentResponse reviewReplenishment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewReplenishmentRequest request) {
        return service.reviewReplenishment(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/replenishments/{id}/receive")
    @PreAuthorize("hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_INVENTORY_CLAIM','PERM_STABLE_CARE_EDIT') or hasRole('CLUB_MANAGER')")
    public ReplenishmentResponse receiveReplenishment(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReceiveReplenishmentRequest request) {
        return service.receiveReplenishment(id, UUID.fromString(jwt.getSubject()), request);
    }
}
