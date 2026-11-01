package com.xiaoying.studio.catalog;

import java.util.List;

public record TemplateView(
        String id, String categoryId, String title, String description,
        String coverUrl, String sampleUrl, int requiredImageCount,
        List<String> acceptedMimeTypes, long maxFileSizeBytes) {}
