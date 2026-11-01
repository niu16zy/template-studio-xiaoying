package com.xiaoying.studio.asset;

import com.xiaoying.studio.catalog.TemplateView;
import com.xiaoying.studio.web.ApiProblem;
import java.io.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageValidator {
    public static final long MAX_PIXELS = 20_000_000;
    public static final int MAX_SIDE = 8192;
    public record CheckedImage(byte[] bytes, String mimeType, String extension, int width, int height) {}

    public CheckedImage check(MultipartFile file, TemplateView template) throws IOException {
        if (file.isEmpty()) throw bad("EMPTY_FILE", "请选择非空图片");
        if (file.getSize() > template.maxFileSizeBytes()) {
            throw new ApiProblem(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "单张图片不能超过 5 MiB");
        }
        String declared = file.getContentType();
        if (declared == null || !template.acceptedMimeTypes().contains(declared)) throw typeError();
        byte[] bytes = file.getBytes();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw typeError();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mime = switch (format) { case "jpeg", "jpg" -> "image/jpeg"; case "png" -> "image/png"; default -> ""; };
                if (!mime.equals(declared) || !template.acceptedMimeTypes().contains(mime)) throw typeError();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw bad("IMAGE_DIMENSIONS", "图片边长不能超过 8192 像素，总像素不能超过 2000 万");
                }
                var image = reader.read(0);
                if (image == null) throw bad("INVALID_IMAGE", "图片已损坏或无法解码");
                image.flush();
                return new CheckedImage(bytes, mime, mime.equals("image/png") ? "png" : "jpg", width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw bad("INVALID_IMAGE", "图片已损坏或无法解码，请重新选择");
        }
    }

    private static ApiProblem typeError() {
        return new ApiProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE", "仅接受内容真实有效的 JPEG / PNG 图片，文件类型需与内容一致");
    }
    private static ApiProblem bad(String code, String message) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, code, message);
    }
}
