package com.xiaoying.studio.asset;

import com.xiaoying.studio.catalog.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetRollbackTest {
    @TempDir Path directory;

    @Test
    void databaseFailureRemovesTheNewlyWrittenFile() throws Exception {
        var catalog = mock(CatalogService.class);
        when(catalog.template("template")).thenReturn(new TemplateView("template", "category", "title", "description", "", "", 1, List.of("image/jpeg"), 5242880));
        var mapper = mock(AssetMapper.class);
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insert(any());
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var storage = new LocalAssetStorage(directory.toString());
        var service = new AssetService(catalog, mapper, new ImageValidator(), storage, manager);
        var file = new MockMultipartFile("file", "image.jpg", "image/jpeg", new ClassPathResource("demo/coast-cover.jpg").getInputStream());
        assertThatThrownBy(() -> service.upload("user", "template", file)).isInstanceOf(IllegalStateException.class);
        try (var files = Files.list(directory.resolve("uploads"))) { assertThat(files.count()).isZero(); }
        verify(manager).rollback(any());
    }
}
