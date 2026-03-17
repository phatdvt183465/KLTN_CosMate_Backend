package com.cosmate.base.crud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CrudService<I, D, F> {

    Page<D> getAll(Pageable pageable, String search, F filter);

    D getById(I id);

    D create(D request);

    D update(I id, D request);

    void delete(I id);
}
