package com.cosmate.service;

import org.springframework.web.multipart.MultipartFile;

public interface FirebaseStorageService {
    /**
     * Uploads a file to Firebase Storage under the given path and returns the public download URL.
     * The `destinationPath` should include folder and filename, e.g. "images/users/123/photo.jpg".
     */
    String uploadFile(MultipartFile file, String destinationPath);

    /**
     * Deletes a file from Firebase Storage by its public URL.
     * Returns true if file existed and was deleted, false otherwise.
     */
    boolean deleteByUrl(String fileUrl);
}
