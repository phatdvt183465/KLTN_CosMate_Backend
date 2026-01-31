package com.cosmate.service;

import com.cosmate.dto.request.AddressRequest;
import com.cosmate.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {
    AddressResponse create(Integer userId, AddressRequest request);
    AddressResponse update(Integer userId, Integer id, AddressRequest request);
    void delete(Integer userId, Integer id);
    AddressResponse getById(Integer userId, Integer id);
    List<AddressResponse> listAllByUser(Integer userId);
}
