package com.cosmate.service.impl;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TempImageStorage {

    // Thư mục cố định trong dự án để tránh mất file khi restart Server
    private final Path root = Paths.get("cosmate-temp-images");

    // Khai báo 1 Executor DUY NHẤT dùng chung để tránh leak bộ nhớ (Thread leak)
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public TempImageStorage() throws IOException {
        // Nếu thư mục cosmate-temp-images chưa tồn tại thì tạo mới
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
    }

    public String save(MultipartFile file) throws IOException {
        // Trả về ID thuần túy (không kèm đuôi .jpg) để lưu vào DB cho sạch sẽ, khớp với URL Frontend gọi
        String id = UUID.randomUUID().toString();

        // File thực tế lưu trên ổ cứng thì vẫn thêm đuôi .jpg để hệ điều hành nhận diện đúng định dạng ảnh
        Path path = root.resolve(id + ".jpg");
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        // Tận dụng executor dùng chung để tự động xóa sau 60 phút
        executor.schedule(() -> delete(id), 60, TimeUnit.MINUTES);

        return id;
    }

    // Trả về kiểu org.springframework.core.io.Resource chuẩn để Controller phân phối dữ liệu
    public Resource load(String id) throws IOException {
        Path path = root.resolve(id);

        // BẪY THÔNG MINH: Nếu tìm file theo ID gốc không thấy và ID chưa có đuôi .jpg,
        // tự động cộng thêm đuôi .jpg vào để tìm file thực tế trên ổ cứng
        if (!Files.exists(path) && !id.endsWith(".jpg")) {
            path = root.resolve(id + ".jpg");
        }

        return new UrlResource(path.toUri());
    }

    public void delete(String id) {
        try {
            Path path = root.resolve(id);

            // Tương tự hàm load, kiểm tra và bù đuôi .jpg nếu cần thiết trước khi xóa
            if (!Files.exists(path) && !id.endsWith(".jpg")) {
                path = root.resolve(id + ".jpg");
            }

            Files.deleteIfExists(path);
            System.out.println("🔥 Đã xóa file tạm thành công: " + id);
        } catch (IOException ignored) {}
    }

    // Tự động dọn dẹp luồng chạy ngầm một cách an toàn khi tắt ứng dụng Spring Boot
    @PreDestroy
    public void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}