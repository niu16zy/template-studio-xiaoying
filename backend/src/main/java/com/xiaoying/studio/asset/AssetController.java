package com.xiaoying.studio.asset;

import com.xiaoying.studio.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final SessionService sessions;
    private final AssetService assets;
    public AssetController(SessionService sessions, AssetService assets) {
        this.sessions = sessions; this.assets = assets;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetService.AssetView> upload(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestHeader("X-Studio-User") String expectedUser,
            @RequestParam String templateId, @RequestPart MultipartFile file) throws IOException {
        var user = sessions.requireExpectedUser(token, expectedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(assets.upload(user.id(), templateId, file));
    }

    @GetMapping("/{id}")
    public AssetService.AssetView metadata(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable String id) {
        return assets.metadata(sessions.requireUser(token).id(), id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token, @PathVariable String id) {
        var file = assets.file(sessions.requireUser(token).id(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.asset().mimeType()))
                .contentLength(file.asset().sizeBytes()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.asset().id() + "\"")
                .body(new FileSystemResource(file.path()));
    }

    public record Selection(@NotBlank String templateId,
                            @NotNull @Size(max = 3) List<@NotBlank String> assetIds) {}
    public record SelectionResult(boolean ready, int assetCount) {}

    @PostMapping("/validate")
    public SelectionResult validate(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestHeader("X-Studio-User") String expectedUser, @Valid @RequestBody Selection selection) {
        var user = sessions.requireExpectedUser(token, expectedUser);
        var selected = assets.validateSelection(user.id(), selection.templateId(), selection.assetIds());
        return new SelectionResult(true, selected.size());
    }
}
