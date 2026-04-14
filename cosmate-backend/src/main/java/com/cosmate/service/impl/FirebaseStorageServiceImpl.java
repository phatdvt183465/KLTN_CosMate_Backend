package com.cosmate.service.impl;

import com.cosmate.configuration.FirebaseConfig;
import com.cosmate.service.FirebaseStorageService;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class FirebaseStorageServiceImpl implements FirebaseStorageService {

    private final FirebaseConfig firebaseConfig;

    @Override
    public String uploadFile(MultipartFile file, String pathInBucket) {
        try {
            Bucket bucket = firebaseConfig.getBucket();
            if (bucket == null) throw new RuntimeException("Firebase bucket not initialized");

            byte[] bytes = file.getBytes();
            // create blob
            Blob blob = bucket.create(pathInBucket, bytes, file.getContentType());

            // Make public (ACL) so the file is accessible via public URL
            blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));

            return String.format("https://storage.googleapis.com/%s/%s", bucket.getName(), pathInBucket);

        } catch (IOException e) {
            throw new RuntimeException("Upload file thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return false;

        try {
            Bucket bucket = firebaseConfig.getBucket();
            if (bucket == null) throw new RuntimeException("Firebase bucket not initialized");

            String bucketName = bucket.getName();
            String marker = "https://storage.googleapis.com/" + bucketName + "/";
            if (!fileUrl.startsWith(marker)) {
                return false;
            }

            String pathInBucket = fileUrl.substring(marker.length());
            pathInBucket = URLDecoder.decode(pathInBucket, StandardCharsets.UTF_8);

            Blob blob = bucket.get(pathInBucket);
            if (blob == null) return false;
            return blob.delete();
        } catch (Exception e) {
            throw new RuntimeException("Xóa file Firebase thất bại: " + e.getMessage(), e);
        }
    }
}
