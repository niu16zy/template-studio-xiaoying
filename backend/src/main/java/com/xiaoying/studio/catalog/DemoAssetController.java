package com.xiaoying.studio.catalog;

import com.xiaoying.studio.web.ResourceNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/demo-assets")
public class DemoAssetController {
    private final CatalogMapper mapper;
    public DemoAssetController(CatalogMapper mapper) { this.mapper = mapper; }

    @GetMapping("/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        if (!name.matches("[a-z0-9-]+\\.(jpg|png)") || mapper.publicAssetReferences(name) == 0) {
            throw new ResourceNotFoundException("示例图片不存在");
        }
        Resource resource = new ClassPathResource("demo/" + name);
        if (!resource.exists()) throw new ResourceNotFoundException("示例图片不存在");
        return ResponseEntity.ok()
                .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
