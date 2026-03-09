package com.cosmate.service.impl;

import com.cosmate.dto.request.AddressRequest;
import com.cosmate.dto.response.AddressResponse;
import com.cosmate.entity.Address;
import com.cosmate.entity.User;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.AddressRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    private AddressResponse toResponse(Address a){
        return AddressResponse.builder()
                .id(a.getId())
                .userId(a.getUser() != null ? a.getUser().getId() : null)
                .name(a.getName())
                .city(a.getCity())
                .district(a.getDistrict())
                .address(a.getAddress())
                .phone(a.getPhone())
                .addressName(a.getAddressName())
                .build();
    }

    @Override
    public AddressResponse create(Integer userId, AddressRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address a = Address.builder()
                .name(request.getName())
                .city(request.getCity())
                .district(request.getDistrict())
                .address(request.getAddress())
                .phone(request.getPhone())
                .addressName(request.getAddressName())
                .user(user)
                .build();
        a = addressRepository.save(a);
        return toResponse(a);
    }

    @Override
    public AddressResponse update(Integer userId, Integer id, AddressRequest request) {
        Address a = addressRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (a.getUser() == null || !a.getUser().getId().equals(userId)) throw new AppException(ErrorCode.FORBIDDEN);

        // Only update fields when request provides a non-null value. Null means "no change".
        if (request.getName() != null) a.setName(request.getName());
        if (request.getCity() != null) a.setCity(request.getCity());
        if (request.getDistrict() != null) a.setDistrict(request.getDistrict());
        if (request.getAddress() != null) a.setAddress(request.getAddress());
        if (request.getPhone() != null) a.setPhone(request.getPhone());
        if (request.getAddressName() != null) a.setAddressName(request.getAddressName());

        a = addressRepository.save(a);
        return toResponse(a);
    }

    @Override
    public void delete(Integer userId, Integer id) {
        Address a = addressRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (a.getUser() == null || !a.getUser().getId().equals(userId)) throw new AppException(ErrorCode.FORBIDDEN);
        addressRepository.deleteById(id);
    }

    @Override
    public AddressResponse getById(Integer userId, Integer id) {
        Address a = addressRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        if (a.getUser() == null || !a.getUser().getId().equals(userId)) throw new AppException(ErrorCode.FORBIDDEN);
        return toResponse(a);
    }

    @Override
    public List<AddressResponse> listAllByUser(Integer userId) {
        // validate user exists
        userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return addressRepository.findAllByUserId(userId).stream().map(this::toResponse).collect(Collectors.toList());
    }
}
