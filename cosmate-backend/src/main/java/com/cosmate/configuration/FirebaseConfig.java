package com.cosmate.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-file:cosmate-firebase.json}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.bucket-name:}")
    private String bucketName;

    private Bucket bucket;

    @PostConstruct
    public void init() {
        InputStream serviceAccountStream = null;
        List<String> tried = new ArrayList<>();
        try {
            // 1) Try classpath via ClassLoader
            try {
                serviceAccountStream = FirebaseConfig.class.getClassLoader().getResourceAsStream(serviceAccountPath);
                tried.add("classpath:" + serviceAccountPath);
                if (serviceAccountStream != null) {
                    log.info("Loaded Firebase service account from classpath resource via ClassLoader: {}", serviceAccountPath);
                }
            } catch (Exception e) {
                log.debug("Failed to load service account from classpath (ClassLoader): {}", e.getMessage());
            }

            // 2) Try working directory path
            if (serviceAccountStream == null) {
                try {
                    File f = new File(serviceAccountPath);
                    tried.add(f.getAbsolutePath());
                    if (f.exists() && f.isFile()) {
                        serviceAccountStream = new FileInputStream(f);
                        log.info("Loaded Firebase service account from file system: {}", f.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.debug("Failed to load service account from filesystem: {}", e.getMessage());
                }
            }

            // 3) Try target/classes (useful in dev)
            if (serviceAccountStream == null) {
                try {
                    String devPath = "target/classes/" + serviceAccountPath;
                    tried.add(new File(devPath).getAbsolutePath());
                    File f2 = new File(devPath);
                    if (f2.exists() && f2.isFile()) {
                        serviceAccountStream = new FileInputStream(f2);
                        log.info("Loaded Firebase service account from build output: {}", f2.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.debug("Failed to load service account from target/classes: {}", e.getMessage());
                }
            }

            // 4) Try service account JSON provided directly via property/env
            if (serviceAccountStream == null && StringUtils.hasText(serviceAccountJson)) {
                tried.add("env:firebase.service-account-json");
                String json = serviceAccountJson.trim();
                if (!json.startsWith("{") && isBase64(json)) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(json);
                        serviceAccountStream = new ByteArrayInputStream(decoded);
                        log.info("Loaded Firebase service account from base64 env/property");
                    } catch (IllegalArgumentException e) {
                        serviceAccountStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                        log.info("Loaded Firebase service account from env/property (plain text)");
                    }
                } else {
                    serviceAccountStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                    log.info("Loaded Firebase service account from env/property (plain text)");
                }
            }

            if (serviceAccountStream == null) {
                log.warn("Firebase service account not found. Tried: {}", tried);
                log.warn("Provide file at path '{}' (relative to working dir) or put it on the classpath, or set 'firebase.service-account-json' property.", serviceAccountPath);
                // Do not throw — allow application to start without Firebase; upload features will fail until properly configured
                return;
            }

            // Build FirebaseOptions
            FirebaseOptions.Builder fbBuilder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream));

            if (StringUtils.hasText(bucketName)) {
                fbBuilder.setStorageBucket(bucketName.trim());
                log.info("Firebase storage bucket set from property: {}", bucketName);
            }

            FirebaseOptions options = fbBuilder.build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully.");
            }

            // create Storage client using the same service account bytes (need fresh stream)
            InputStream credsStream = null;
            try {
                credsStream = FirebaseConfig.class.getClassLoader().getResourceAsStream(serviceAccountPath);
                if (credsStream == null) {
                    File f = new File(serviceAccountPath);
                    if (f.exists() && f.isFile()) credsStream = new FileInputStream(f);
                    else {
                        String devPath = "target/classes/" + serviceAccountPath;
                        File f2 = new File(devPath);
                        if (f2.exists() && f2.isFile()) credsStream = new FileInputStream(f2);
                        else if (StringUtils.hasText(serviceAccountJson)) credsStream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
                    }
                }

                if (credsStream == null) {
                    log.warn("Could not build explicit Storage client creds stream; Storage client may use default credentials");
                } else {
                    Storage storage = StorageOptions.newBuilder()
                            .setCredentials(GoogleCredentials.fromStream(credsStream))
                            .build()
                            .getService();

                    if (StringUtils.hasText(bucketName)) {
                        this.bucket = storage.get(bucketName);
                        if (this.bucket == null) {
                            log.warn("Configured Firebase bucket '{}' not found in project (or service account has no access).", bucketName);
                        } else {
                            log.info("Obtained bucket: {}", this.bucket.getName());
                        }
                    } else {
                        log.info("No bucketName configured; skipping explicit bucket retrieval");
                    }
                }
            } finally {
                if (credsStream != null) try { credsStream.close(); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage(), e);
            // Don't prevent application from starting; log and return
            return;
        } finally {
            if (serviceAccountStream != null) try { serviceAccountStream.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isBase64(String s) {
        String trimmed = s.replaceAll("\r|\n", "").trim();
        if (trimmed.length() % 4 != 0) return false;
        return trimmed.matches("^[A-Za-z0-9+/=\\r\\n]+$");
    }

    public Bucket getBucket() {
        return bucket;
    }
}
