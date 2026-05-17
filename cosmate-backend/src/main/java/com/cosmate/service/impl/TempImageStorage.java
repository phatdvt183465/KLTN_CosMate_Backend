package com.cosmate.service.impl;

import com.google.api.client.util.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class TempImageStorage {

    private final Path root;

    public TempImageStorage() throws IOException {
        // dùng thư mục temp hệ điều hành
        this.root = Files.createTempDirectory("ws-images");
    }

    public String save(MultipartFile file) throws IOException {
        String id = UUID.randomUUID() + ".jpg";
        Path path = root.resolve(id);
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        // auto delete sau 5 phút
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> delete(id), 60, TimeUnit.MINUTES);

        return id;
    }

    public UrlResource load(String id) throws IOException {
        Path path = root.resolve(id);
        return new UrlResource(path.toUri());
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(root.resolve(id));
        } catch (IOException ignored) {}
    }
}
