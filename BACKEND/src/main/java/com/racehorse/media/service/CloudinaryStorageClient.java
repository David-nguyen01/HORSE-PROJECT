package com.racehorse.media.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.racehorse.common.exception.ExternalServiceException;
import com.racehorse.media.config.CloudinaryProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class CloudinaryStorageClient implements CloudStorageClient {

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryStorageClient(Cloudinary cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public CloudAsset upload(byte[] content, String originalFilename, UUID horseId) {
        return upload(content, originalFilename, "horses/" + horseId);
    }

    @Override
    public CloudAsset upload(byte[] content, String originalFilename, String folderScope) {
        requireConfigured();
        try {
            String safeScope=folderScope==null?"shared":folderScope.replaceAll("[^a-zA-Z0-9/_-]","-");
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "resource_type", "auto",
                    "folder", normalizedFolder() + "/" + safeScope,
                    "use_filename", true,
                    "unique_filename", true,
                    "overwrite", false));
            return new CloudAsset(
                    required(result, "asset_id"),
                    required(result, "public_id"),
                    required(result, "resource_type"),
                    optional(result, "format"),
                    Long.parseLong(required(result, "bytes")),
                    required(result, "secure_url"));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ExternalServiceException externalServiceException) {
                throw externalServiceException;
            }
            throw new ExternalServiceException("Không thể tải file lên Cloudinary", exception);
        }
    }

    @Override
    public void delete(String publicId, String resourceType) {
        requireConfigured();
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "invalidate", true));
        } catch (IOException | RuntimeException exception) {
            throw new ExternalServiceException("Không thể xóa file khỏi Cloudinary", exception);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ExternalServiceException(
                    "Cloudinary chưa được cấu hình; cần CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY và CLOUDINARY_API_SECRET");
        }
    }

    private String normalizedFolder() {
        return properties.folder() == null || properties.folder().isBlank()
                ? "racehorse"
                : properties.folder().trim();
    }

    private String required(Map<?, ?> result, String key) {
        Object value = result.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ExternalServiceException("Cloudinary không trả về trường bắt buộc: " + key);
        }
        return value.toString();
    }

    private String optional(Map<?, ?> result, String key) {
        Object value = result.get(key);
        return value == null ? null : value.toString();
    }
}
