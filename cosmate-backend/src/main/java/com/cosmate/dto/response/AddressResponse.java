package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.io.Serializable;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddressResponse implements Serializable {
    Integer id;
    Integer userId;
    String name;
    String city;
    String district;
    String address;
    String phone;
    String addressName;
}
