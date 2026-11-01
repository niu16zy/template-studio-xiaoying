package com.xiaoying.studio.asset;

import com.xiaoying.studio.catalog.TemplateView;
import com.xiaoying.studio.web.ApiProblem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ImageValidatorTest {
    private final TemplateView template = new TemplateView("t", "c", "title", "description", "/cover", "/sample",
            1, List.of("image/jpeg", "image/png"), 5242880);
    @Test
    void acceptsActualPngAndRejectsOversizedDimensionsBeforeDecodingPixels() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB), "png", output);
        var valid = new ImageValidator().check(new MockMultipartFile("file", "sample.png", "image/png", output.toByteArray()), template);
        assertThat(valid.width()).isEqualTo(24);
        assertThat(valid.height()).isEqualTo(16);
        output.reset();
        ImageIO.write(new BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        var oversized = new MockMultipartFile("file", "large.png", "image/png", output.toByteArray());
        assertThatThrownBy(() -> new ImageValidator().check(oversized, template))
                .isInstanceOfSatisfying(ApiProblem.class, error -> assertThat(error.code()).isEqualTo("IMAGE_DIMENSIONS"));
    }
}
