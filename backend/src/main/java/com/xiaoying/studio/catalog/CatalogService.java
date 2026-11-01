package com.xiaoying.studio.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.web.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogMapper mapper;
    private final ObjectMapper json;

    public CatalogService(CatalogMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<Category> categories() {
        return mapper.categories();
    }

    public List<TemplateView> templates(String categoryId) {
        return mapper.templates(categoryId).stream().map(this::view).toList();
    }

    public TemplateView template(String id) {
        var row = mapper.template(id);
        if (row == null) throw new ResourceNotFoundException("模板不存在");
        return view(row);
    }

    private TemplateView view(TemplateRow row) {
        try {
            List<String> accepted = json.readValue(row.acceptedMimeTypes(), new TypeReference<>() {});
            return new TemplateView(row.id(), row.categoryId(), row.title(), row.description(),
                    "/api/demo-assets/" + row.coverPath(), "/api/demo-assets/" + row.samplePath(),
                    row.requiredImageCount(), accepted, row.maxFileSizeBytes());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid template MIME configuration: " + row.id(), exception);
        }
    }
}
