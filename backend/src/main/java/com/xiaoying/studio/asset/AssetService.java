package com.xiaoying.studio.asset;

import com.xiaoying.studio.catalog.CatalogService;
import com.xiaoying.studio.web.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
    private final CatalogService catalog;
    private final AssetMapper mapper;
    private final ImageValidator validator;
    private final LocalAssetStorage storage;
    private final TransactionTemplate transactions;

    public AssetService(CatalogService catalog, AssetMapper mapper, ImageValidator validator,
                        LocalAssetStorage storage, org.springframework.transaction.PlatformTransactionManager manager) {
        this.catalog = catalog; this.mapper = mapper; this.validator = validator; this.storage = storage;
        this.transactions = new TransactionTemplate(manager);
    }

    public record AssetView(String id, String previewUrl, String mimeType, long sizeBytes, int width, int height) {}
    public record AssetFile(AssetRow asset, Path path) {}

    public AssetView upload(String userId, String templateId, MultipartFile file) throws IOException {
        var template = catalog.template(templateId);
        var image = validator.check(file, template);
        String id = UUID.randomUUID().toString();
        String relative = storage.write(id, image);
        String original = Objects.toString(file.getOriginalFilename(), "image").replaceAll("[\\p{Cntrl}]", "");
        if (original.length() > 255) original = original.substring(0, 255);
        var asset = new AssetRow(id, userId, templateId, relative, original, image.mimeType(),
                image.bytes().length, image.width(), image.height());
        try {
            transactions.executeWithoutResult(status -> mapper.insert(asset));
        } catch (RuntimeException exception) {
            try { storage.delete(relative); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
        return view(asset);
    }

    public AssetView metadata(String userId, String id) { return view(owned(userId, id)); }

    public AssetFile file(String userId, String id) {
        var asset = owned(userId, id);
        try { return new AssetFile(asset, storage.readable(asset.storagePath())); }
        catch (IOException exception) { throw new ResourceNotFoundException("素材文件不存在，请重新上传"); }
    }

    // Reusable by stage 3: call again inside task acceptance. A successful preflight is not a reservation.
    public List<AssetRow> validateSelection(String userId, String templateId, List<String> ids) {
        var template = catalog.template(templateId);
        if (ids == null || ids.size() != template.requiredImageCount()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "IMAGE_COUNT", "请准备 " + template.requiredImageCount() + " 张图片");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "DUPLICATE_ASSET", "不同上传位不能重复引用同一份素材");
        }
        var assets = new ArrayList<AssetRow>();
        for (String id : ids) {
            var asset = owned(userId, id);
            if (!asset.templateId().equals(templateId) || !template.acceptedMimeTypes().contains(asset.mimeType())
                    || asset.sizeBytes() > template.maxFileSizeBytes()) {
                throw new ApiProblem(HttpStatus.BAD_REQUEST, "ASSET_MISMATCH", "素材与当前模板要求不匹配，请重新上传");
            }
            file(userId, id);
            assets.add(asset);
        }
        return List.copyOf(assets);
    }

    private AssetRow owned(String userId, String id) {
        var asset = mapper.owned(id, userId);
        if (asset == null) throw new ResourceNotFoundException("素材不存在或不属于当前身份");
        return asset;
    }
    private AssetView view(AssetRow asset) {
        return new AssetView(asset.id(), "/api/assets/" + asset.id() + "/content", asset.mimeType(),
                asset.sizeBytes(), asset.width(), asset.height());
    }
}
