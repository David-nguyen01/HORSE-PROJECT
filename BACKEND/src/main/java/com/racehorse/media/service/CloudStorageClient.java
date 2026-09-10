package com.racehorse.media.service;

import java.util.UUID;

public interface CloudStorageClient {

    CloudAsset upload(byte[] content, String originalFilename, UUID horseId);

    void delete(String publicId, String resourceType);
}
