package com.racehorse.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInventoryCatalogRequest(
        @NotNull(message = "Loại catalog không được để trống")
        CatalogType type,

        @NotBlank(message = "Tên không được để trống")
        @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
        String name,

        @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
        String description,

        @Size(max = 100, message = "Đơn vị không được vượt quá 100 ký tự")
        String unit
) {
    public enum CatalogType {
        CATEGORY,
        ITEM
    }
}
