package com.racehorse.media.service;

import java.util.UUID;

public interface CloudStorageClient {

    CloudAsset upload(byte[] content, String originalFilename, UUID horseId);
    CloudAsset upload(byte[] content, String originalFilename, String folderPath);

    void delete(String publicId, String resourceType);
}
