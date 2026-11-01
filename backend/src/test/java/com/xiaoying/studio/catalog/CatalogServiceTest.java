package com.xiaoying.studio.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogMapper mapper = mock(CatalogMapper.class);
    private final CatalogService service = new CatalogService(mapper, new ObjectMapper());

    @Test
    void missingTemplateIsNotAnEmptySuccess() {
        assertThatThrownBy(() -> service.template("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exposesPublicUrlsAndTypedMimeRequirementsWithoutStoragePaths() {
        when(mapper.template("coast")).thenReturn(new TemplateRow("coast", "travel", "海岸", "描述",
                "coast-cover.jpg", "coast-result.png", 2, "[\"image/jpeg\",\"image/png\"]", 5242880));
        var view = service.template("coast");
        assertThat(view.coverUrl()).isEqualTo("/api/demo-assets/coast-cover.jpg");
        assertThat(view.acceptedMimeTypes()).containsExactly("image/jpeg", "image/png");
        assertThat(view.requiredImageCount()).isEqualTo(2);
    }
}
