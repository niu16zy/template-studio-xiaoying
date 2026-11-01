package com.xiaoying.studio.catalog;

import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogIT {
    @Autowired MockMvc mvc;
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogService catalog;

    @Test
    void flywayCanRunAgainWithoutDuplicatingSeedData() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM template", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM template_category", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_user", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creation_task", Integer.class)).isZero();
    }

    @Test
    void mybatisReadsFiltersAndSerializesTheContract() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/templates").param("categoryId", "travel"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requiredImageCount").value(2))
                .andExpect(jsonPath("$[0].acceptedMimeTypes[0]").value("image/jpeg"));
        mvc.perform(get("/api/templates").param("categoryId", "unknown"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/templates/coastal-postcard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("海岸来信"));
        mvc.perform(get("/api/templates/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void everySeedImageIsPackagedDecodableAndPubliclyServed() throws Exception {
        for (var template : catalog.templates(null)) {
            for (String url : new String[]{template.coverUrl(), template.sampleUrl()}) {
                String name = url.substring(url.lastIndexOf('/') + 1);
                try (var input = new ClassPathResource("demo/" + name).getInputStream()) {
                    var image = ImageIO.read(input);
                    assertThat(image).as(name).isNotNull();
                    assertThat(image.getWidth()).isEqualTo(720);
                    assertThat(image.getHeight()).isEqualTo(1280);
                }
                mvc.perform(get(url)).andExpect(status().isOk())
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
            }
        }
        mvc.perform(get("/api/demo-assets/not-seeded.png")).andExpect(status().isNotFound());
    }
}
