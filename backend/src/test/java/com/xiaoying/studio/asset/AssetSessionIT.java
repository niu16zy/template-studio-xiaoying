package com.xiaoying.studio.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AssetSessionIT {
    private static final String A = "00000000-0000-4000-8000-000000000001";
    private static final String B = "00000000-0000-4000-8000-000000000002";
    private static final Path STORAGE = temporaryStorage();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private static Path temporaryStorage() {
        try { return Files.createTempDirectory("studio-assets-it-"); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("studio.storage-root", () -> STORAGE.toString());
    }

    private Cookie login(String user, Cookie previous) throws Exception {
        var request = post("/api/session").header("X-Studio-Request", "1")
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("userId", user)));
        if (previous != null) request.cookie(previous);
        var response = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user)).andReturn().getResponse();
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/");
        return new Cookie(SessionService.COOKIE_NAME, header.split(";", 2)[0].split("=", 2)[1]);
    }

    private MockMultipartFile image() throws Exception {
        return new MockMultipartFile("file", "../../untrusted.jpg", "image/jpeg",
                new ClassPathResource("demo/coast-cover.jpg").getInputStream());
    }

    private String upload(Cookie session, String template) throws Exception {
        var result = mvc.perform(multipart("/api/assets").file(image()).param("templateId", template)
                .cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("image/jpeg")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void sessionsPersistRotateAndEnforceResourceOwnership() throws Exception {
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
        Cookie firstA = login(A, null);
        mvc.perform(get("/api/session").cookie(firstA)).andExpect(jsonPath("$.displayName").value("演示用户 A"));
        String assetId = upload(firstA, "afternoon-light");
        String path = jdbc.queryForObject("SELECT storage_path FROM asset WHERE id = ?", String.class, assetId);
        assertThat(path).startsWith("uploads/").doesNotContain("untrusted", "..");
        assertThat(Files.exists(STORAGE.resolve(path))).isTrue();
        mvc.perform(get("/api/assets/" + assetId + "/content").cookie(firstA)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("image/jpeg"));
        Cookie userB = login(B, firstA);
        mvc.perform(get("/api/session").cookie(firstA)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/assets/" + assetId).cookie(userB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/assets/" + assetId + "/content").cookie(userB)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/assets").file(image()).param("templateId", "afternoon-light")
                .cookie(userB).header("X-Studio-Request", "1").header("X-Studio-User", A))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CHANGED"));
        Cookie secondA = login(A, userB);
        mvc.perform(get("/api/assets/" + assetId).cookie(secondA)).andExpect(status().isOk());
        assertThat(new LocalAssetStorage(STORAGE.toString()).readable(path)).exists();
        jdbc.update("UPDATE user_session SET expires_at = '2000-01-01' WHERE user_id = ?", A);
        mvc.perform(get("/api/session").cookie(secondA)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingUserSpoofedMimeOversizedAndCorruptImagesWithoutRows() throws Exception {
        Cookie session = login(A, null);
        int initial = jdbc.queryForObject("SELECT COUNT(*) FROM asset", Integer.class);
        mvc.perform(multipart("/api/assets").file(image()).param("templateId", "afternoon-light")
                .header("X-Studio-Request", "1").header("X-Studio-User", A)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/assets").file(image()).param("templateId", "missing")
                .cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A)).andExpect(status().isNotFound());
        var badFiles = List.of(
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "mismatch.png", "image/png", image().getBytes()),
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "huge.jpg", "image/jpeg", new byte[5242881]));
        for (var file : badFiles) {
            mvc.perform(multipart("/api/assets").file(file).param("templateId", "afternoon-light")
                    .cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A))
                    .andExpect(status().is4xxClientError());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset", Integer.class)).isEqualTo(initial);
        mvc.perform(post("/api/session").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesExactCountUniqueIdsTemplateAndOwnerThenDetectsMissingFiles() throws Exception {
        Cookie session = login(A, null);
        String first = upload(session, "coastal-postcard");
        String second = upload(session, "coastal-postcard");
        var request = java.util.Map.of("templateId", "coastal-postcard", "assetIds", List.of(first, second));
        mvc.perform(post("/api/assets/validate").cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A)
                .contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
        for (var ids : List.of(List.of(first), List.of(first, first))) {
            mvc.perform(post("/api/assets/validate").cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A)
                    .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("templateId", "coastal-postcard", "assetIds", ids))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/assets/validate").cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A)
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("templateId", "afternoon-light", "assetIds", List.of(first)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSET_MISMATCH"));
        Cookie userB = login(B, null);
        mvc.perform(post("/api/assets/validate").cookie(userB).header("X-Studio-Request", "1").header("X-Studio-User", B)
                .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isNotFound());
        String path = jdbc.queryForObject("SELECT storage_path FROM asset WHERE id = ?", String.class, first);
        Files.delete(STORAGE.resolve(path));
        mvc.perform(post("/api/assets/validate").cookie(session).header("X-Studio-Request", "1").header("X-Studio-User", A)
                .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isNotFound());
    }
}
