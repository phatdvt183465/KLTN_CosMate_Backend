package com.cosmate.entity;

import com.cosmate.base.dataio.template.annotation.ImportEntity;
import com.cosmate.base.dataio.importer.annotation.ImportField;
import com.cosmate.base.dataio.importer.annotation.ImportHash;
import com.cosmate.base.dataio.exporter.annotation.ExportEntity;
import com.cosmate.base.dataio.exporter.annotation.ExportField;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ImportEntity("user")
@ExportEntity(fileName = "danh_sach_nguoi_dung", sheetName = "User Data")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ExportField(name = "ID")
    private Integer id;

    @ImportField(name = "Username", required = true)
    @ExportField(name = "Tên đăng nhập")
    @Column(length = 100, name = "username")
    private String username;

    // Tự động băm password khi import từ file Excel
    @ImportField(name = "Mật khẩu", required = true)
    @ImportHash
    @Column(length = 255, name = "password_hash")
    private String passwordHash;

    @ImportField(name = "Email", required = true)
    @ExportField(name = "Email")
    @Column(length = 255, unique = true, name = "email")
    private String email;

    @ImportField(name = "Họ và tên")
    @ExportField(name = "Họ và tên")
    @Column(length = 255, name = "full_name")
    private String fullName;

    @Column(length = 255, name = "avatar_url")
    private String avatarUrl; // Admin không cần import avatar qua Excel

    @ImportField(name = "Số điện thoại")
    @ExportField(name = "SĐT")
    @Column(length = 20, name = "phone")
    private String phone;

    @ImportField(name = "Trạng thái")
    @ExportField(name = "Trạng thái")
    @Column(length = 20, name = "status")
    private String status;

    @ExportField(name = "Ngày tham gia", dateFormat = "dd/MM/yyyy HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ExportField(name = "Số token")
    @Column(name = "number_of_token", nullable = false, columnDefinition = "int default 100")
    private Integer numberOfToken;

    @Column(name = "current_archetype", length = 50)
    private String currentArchetype;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @Column(name = "ai_tokens")
    private Integer aiTokens = 100;
}