package com.racehorse.media.service;

public record CloudAsset(
        String assetId,
        String publicId,
        String resourceType,
        String format,
        long bytes,
        String secureUrl
) {
}
