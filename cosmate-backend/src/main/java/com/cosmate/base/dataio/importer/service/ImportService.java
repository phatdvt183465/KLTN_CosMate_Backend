package com.cosmate.base.dataio.importer.service;

import com.cosmate.base.dataio.importer.result.ImportResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.multipart.MultipartFile;

public interface ImportService {

    <T, ID> ImportResult importFile(
            MultipartFile file,
            Class<T> entityClass,
            JpaRepository<T, ID> repository
    );

}
