package com.xiaoying.studio.asset;

public record AssetRow(String id, String userId, String templateId, String storagePath,
                       String originalName, String mimeType, long sizeBytes, int width, int height) {}
