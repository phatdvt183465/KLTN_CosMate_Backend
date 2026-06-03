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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TempImageStorage {

    // Thư mục cố định trong dự án để tránh mất file khi restart Server
    private final Path root = Paths.get("cosmate-temp-images");

    // Khai báo 1 Executor DUY NHẤT dùng chung để tránh leak bộ nhớ (Thread leak)
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Các đuôi file hợp lệ (không có dấu chấm), gồm ảnh và video
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "mp4", "mov", "avi", "webm", "mkv", "mpeg", "3gp"
    ));

    // Map MIME type -> extension (thử suy đoán nếu filename không có đuôi)
    private static final Map<String, String> MIME_TO_EXT;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("image/jpeg", "jpg");
        m.put("image/jpg", "jpg");
        m.put("image/png", "png");
        m.put("image/gif", "gif");
        m.put("image/bmp", "bmp");
        m.put("image/webp", "webp");
        m.put("video/mp4", "mp4");
        m.put("video/quicktime", "mov");
        m.put("video/x-msvideo", "avi");
        m.put("video/webm", "webm");
        m.put("video/x-matroska", "mkv");
        m.put("video/mpeg", "mpeg");
        m.put("video/3gpp", "3gp");
        MIME_TO_EXT = Collections.unmodifiableMap(m);
    }

    public TempImageStorage() throws IOException {
        // Nếu thư mục cosmate-temp-images chưa tồn tại thì tạo mới
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
    }

    public String save(MultipartFile file) throws IOException {
        // Trả về ID thuần túy (không kèm đuôi .jpg) để lưu vào DB cho sạch sẽ, khớp với URL Frontend gọi
        String id = UUID.randomUUID().toString();

        // Xác định đuôi hợp lệ từ tên file hoặc MIME type
        String ext = detectExtension(file);

        // File thực tế lưu trên ổ cứng thì thêm đuôi tương ứng để hệ điều hành nhận diện đúng định dạng
        Path path = root.resolve(id + "." + ext);
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        // Tận dụng executor dùng chung để tự động xóa sau 60 phút
        executor.schedule(() -> delete(id), 60, TimeUnit.MINUTES);

        return id;
    }

    // Thử xác định đuôi file hợp lệ từ MultipartFile
    private String detectExtension(MultipartFile file) throws IOException {
        // 1) Thử lấy từ original filename
        String original = file.getOriginalFilename();
        if (original != null) {
            int idx = original.lastIndexOf('.');
            if (idx >= 0 && idx < original.length() - 1) {
                String ext = original.substring(idx + 1).toLowerCase(Locale.ROOT);
                if (ALLOWED_EXTENSIONS.contains(ext)) {
                    return ext;
                }
            }
        }

        // 2) Thử lấy từ content type
        String contentType = file.getContentType();
        if (contentType != null) {
            String mapped = MIME_TO_EXT.get(contentType.toLowerCase(Locale.ROOT));
            if (mapped != null) return mapped;
            // Một số browser gửi image/jpg vs image/jpeg etc. Try startsWith
            for (Map.Entry<String, String> e : MIME_TO_EXT.entrySet()) {
                if (contentType.toLowerCase(Locale.ROOT).startsWith(e.getKey().split("/")[0] + "/")) {
                    return e.getValue();
                }
            }
        }

        // Nếu không đoán được, từ chối để tránh lưu file không xác định
        throw new IOException("Unsupported or unknown file type");
    }

    // Trả về kiểu org.springframework.core.io.Resource chuẩn để Controller phân phối dữ liệu
    public Resource load(String id) throws IOException {
        // Nếu id đã bao gồm đuôi và file tồn tại thì trả luôn
        Path path = root.resolve(id);
        if (Files.exists(path)) {
            return new UrlResource(path.toUri());
        }

        // Nếu chưa có đuôi, thử tìm file với các đuôi được phép
        if (!id.contains(".")) {
            for (String ext : ALLOWED_EXTENSIONS) {
                Path p = root.resolve(id + "." + ext);
                if (Files.exists(p)) {
                    return new UrlResource(p.toUri());
                }
            }
        }

        throw new IOException("File not found: " + id);
    }

    public void delete(String id) {
        try {
            Path path = root.resolve(id);

            // Nếu id trỏ trực tiếp tới file tồn tại
            if (Files.exists(path)) {
                Files.deleteIfExists(path);
                System.out.println("🔥 Đã xóa file tạm thành công: " + id);
                return;
            }

            // Nếu id chưa có đuôi, thử xóa file với các đuôi hợp lệ (xóa file đầu tiên tìm thấy)
            if (!id.contains(".")) {
                for (String ext : ALLOWED_EXTENSIONS) {
                    Path p = root.resolve(id + "." + ext);
                    if (Files.exists(p)) {
                        Files.deleteIfExists(p);
                        System.out.println("🔥 Đã xóa file tạm thành công: " + id + "." + ext);
                        return;
                    }
                }
            }

            // Nếu không tìm thấy, nothing to do
            System.out.println("⚠️ Không tìm thấy file để xóa: " + id);
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