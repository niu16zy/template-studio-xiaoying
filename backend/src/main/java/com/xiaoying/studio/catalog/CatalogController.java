package com.xiaoying.studio.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/categories")
    public List<Category> categories() { return service.categories(); }

    @GetMapping("/templates")
    public List<TemplateView> templates(@RequestParam(required = false) String categoryId) {
        return service.templates(categoryId == null || categoryId.isBlank() ? null : categoryId);
    }

    @GetMapping("/templates/{id}")
    public TemplateView template(@PathVariable String id) { return service.template(id); }
}
