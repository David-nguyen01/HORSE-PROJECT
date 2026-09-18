package com.racehorse.media.service;

import java.util.UUID;

public interface CloudStorageClient {

    CloudAsset upload(byte[] content, String originalFilename, UUID horseId);
    CloudAsset upload(byte[] content, String originalFilename, String folderPath);

    CloudAsset upload(byte[] content, String originalFilename, String folderScope);

    void delete(String publicId, String resourceType);
}
