package com.cosmate.base.crud;

import com.cosmate.base.crud.dto.CrudDto;
import com.cosmate.base.spec.AutoSpecBuilder;
import com.cosmate.exception.BadRequestException;
import com.cosmate.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

@Transactional
public abstract class CrudServiceImpl<
        E,
        I,
        D extends CrudDto<I>,
        F> implements CrudService<I, D, F> {

    @Autowired
    protected  AutoSpecBuilder autoSpecBuilder;

    protected abstract BaseCrudRepository<E, I> getRepository();
    protected abstract BaseCrudMapper<E, D> getMapper();
    protected abstract String[] searchableFields();

    protected void beforeCreate(E entity, D request) {}
    protected void afterCreate(E entity, D request) {}

    protected void beforeUpdate(E entity, D request) {}
    protected void afterUpdate(E entity, D request) {}

    protected void beforeDelete(E entity) {}
    protected void afterDelete(E entity) {}

    protected D createEntity(D request) {

        E entity = getMapper().toEntity(request);

        beforeCreate(entity, request);

        E saved = getRepository().save(entity);

        afterCreate(saved, request);

        return getMapper().toResponse(saved);
    }

    protected D updateEntity(I id, D request) {

        if (!id.equals(request.getId())) {
            throw new BadRequestException("ID mismatch");
        }

        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));

        beforeUpdate(entity, request);

        getMapper().update(entity, request);

        E saved = getRepository().save(entity);

        afterUpdate(saved, request);

        return getMapper().toResponse(saved);
    }

    protected void deleteEntity(I id) {

        E entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));

        beforeDelete(entity);

        getRepository().delete(entity);

        afterDelete(entity);
    }

    protected D getByIdEntity(I id) {
        return getRepository().findById(id)
                .map(getMapper()::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));
    }

    protected Page<D> getAllEntity(Pageable pageable,String search, F filter) {

        Specification<E> spec = Specification.where(null);

        Specification<E> filterSpec = autoSpecBuilder.build(filter);
        Specification<E> searchSpec = buildSearchSpec(search);

        if (filterSpec != null) spec = spec.and(filterSpec);
        if (searchSpec != null) spec = spec.and(searchSpec);

        return getRepository().findAll(spec, pageable)
                .map(getMapper()::toResponse);
    }

    private Specification<E> buildSearchSpec(String keyword) {

        if (keyword == null || keyword.isBlank()) return null;

        String likePattern = "%" + keyword.toLowerCase() + "%";

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            for (String field : searchableFields()) {

                try {
                    var path = root.get(field);
                    if (!String.class.equals(path.getJavaType())) {
                        continue;
                    }
                    predicates.add(cb.like(cb.lower(path.as(String.class)), likePattern));

                } catch (IllegalArgumentException ex) {
                    throw new RuntimeException("Invalid searchable field: " + field, ex);
                }
            }

            if (predicates.isEmpty()) {
                return null;
            }

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
