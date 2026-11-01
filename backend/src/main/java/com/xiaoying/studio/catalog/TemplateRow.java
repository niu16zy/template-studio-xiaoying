package com.xiaoying.studio.catalog;

public record TemplateRow(
        String id, String categoryId, String title, String description,
        String coverPath, String samplePath, int requiredImageCount,
        String acceptedMimeTypes, long maxFileSizeBytes) {}
