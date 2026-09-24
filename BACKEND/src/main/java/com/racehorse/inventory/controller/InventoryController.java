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
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<InventoryItemResponse> listItems(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lowStockOnly) {
        return service.listItems(category, lowStockOnly);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public InventoryItemResponse getItem(@PathVariable UUID id) {
        return service.getItem(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public InventoryItemResponse createItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInventoryItemRequest request) {
        return service.createItem(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/items/{id}/stock")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT')")
    public InventoryItemResponse updateStock(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStockRequest request) {
        return service.updateStock(id, UUID.fromString(jwt.getSubject()), request);
    }

    // ==================== REPLENISHMENTS ====================

    @GetMapping("/replenishments")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<ReplenishmentResponse> listReplenishments(
            @RequestParam(required = false) String status) {
        return service.listReplenishments(status);
    }

    @GetMapping("/replenishments/{id}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public ReplenishmentResponse getReplenishment(@PathVariable UUID id) {
        return service.getReplenishment(id);
    }

    @PostMapping("/replenishments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT')")
    public ReplenishmentResponse createReplenishment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReplenishmentRequest request) {
        return service.createReplenishment(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/replenishments/{id}/review")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_APPROVE')")
    public ReplenishmentResponse reviewReplenishment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewReplenishmentRequest request) {
        return service.reviewReplenishment(id, UUID.fromString(jwt.getSubject()), request);
    }
}
