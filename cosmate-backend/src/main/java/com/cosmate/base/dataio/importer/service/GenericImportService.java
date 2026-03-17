package com.cosmate.base.dataio.importer.service;

import com.cosmate.base.dataio.importer.annotation.ImportHash;
import com.cosmate.base.dataio.importer.mapper.GenericImportMapper;
import com.cosmate.base.dataio.importer.parser.FileParser;
import com.cosmate.base.dataio.importer.parser.ParserFactory;
import com.cosmate.base.dataio.importer.result.ImportResult;
import com.cosmate.base.dataio.importer.result.RowError;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class GenericImportService implements ImportService {

    private final ParserFactory parserFactory;
    private final GenericImportMapper mapper;
    private final PasswordEncoder passwordEncoder; // Đổi HashService thành PasswordEncoder

    @Override
    @Transactional
    public <T, ID> ImportResult importFile(
            MultipartFile file,
            Class<T> entityClass,
            JpaRepository<T, ID> repository
    ) {
        ImportResult result = new ImportResult();
        try {
            FileParser parser = parserFactory.getParser(file.getOriginalFilename());
            List<Map<String, String>> rows = parser.parse(file.getInputStream());

            List<T> batch = new ArrayList<>();
            int batchSize = 500; // Cứ gom đủ 500 dòng thì đẩy 1 lần
            int rowIndex = 2;

            for (Map<String, String> row : rows) {
                try {
                    T entity = mapper.map(row, entityClass);
                    applyHash(entity);
                    batch.add(entity); // Cho vào danh sách chờ
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.getErrors().add(new RowError(rowIndex, e.getMessage()));
                    result.setFailureCount(result.getFailureCount() + 1);
                }
                rowIndex++;

                // Nếu danh sách chờ đã đầy -> Đẩy xuống DB
                if (batch.size() >= batchSize) {
                    repository.saveAll(batch);
                    batch.clear(); // Xóa danh sách chờ để gom đợt tiếp theo
                }
            }

            // Đẩy nốt những thằng còn sót lại cuối cùng
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
            }

        } catch (Exception e) {
            throw new RuntimeException("Import failed", e);
        }
        return result;
    }

    private void applyHash(Object entity) throws IllegalAccessException {

        for (Field field : entity.getClass().getDeclaredFields()) {

            ImportHash ann = field.getAnnotation(ImportHash.class);
            if (ann == null) continue;

            field.setAccessible(true);

            Object val = field.get(entity);

            // Băm mật khẩu bằng PasswordEncoder chuẩn của Spring
            if (val instanceof String raw && !isHashed(raw)) {
                field.set(entity, passwordEncoder.encode(raw));
            }
        }
    }

    private boolean isHashed(String v) {
        return v.startsWith("$2");
    }
}