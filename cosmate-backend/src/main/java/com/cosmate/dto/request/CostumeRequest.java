package com.cosmate.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CostumeRequest {
    @NotBlank(message = "COSTUME_NAME_INVALID")
    @Size(max = 255)
    private String name;

    @Size(max = 4000)
    private String description;

    private String size;

    @Size(max = 255)
    private String rentPurpose;

    @Min(value = 1, message = "NUMBER_OF_ITEMS_INVALID")
    private Integer numberOfItems;

    @NotNull(message = "PRICE_PER_DAY_INVALID")
    @DecimalMin(value = "0.0", inclusive = true, message = "PRICE_PER_DAY_INVALID")
    private BigDecimal pricePerDay;

    @DecimalMin(value = "0.0", inclusive = true, message = "DEPOSIT_AMOUNT_INVALID")
    private BigDecimal depositAmount;

    @DecimalMin(value = "0.0", inclusive = true, message = "COST_INVALID")
    private BigDecimal cost;

    @Min(value = 0, message = "RENT_DISCOUNT_INVALID")
    private Integer rentDiscount;

    @NotNull(message = "PROVIDER_ID_INVALID")
    @Min(value = 1, message = "PROVIDER_ID_INVALID")
    private Integer providerId;

    private List<MultipartFile> imageFiles;
    
    private List<MultipartFile> videoFiles;

    @Size(max = 10000)
    private String surcharges;

    @Size(max = 10000)
    private String accessories;

    @Size(max = 10000)
    private String rentalOptions;

    @Size(max = 20)
    @Schema(description = "Allowed values: MALE, FEMALE, UNISEX, GENDERLESS", allowableValues = {"MALE", "FEMALE", "UNISEX", "GENDERLESS"}, nullable = true)
    private String gender;

    @Size(max = 3, message = "Chỉ được chọn tối đa 3 nhân vật cho mỗi trang phục")
    private List<Integer> characterIds;

    public void setCharacterIds(List<Integer> characterIds) {
        this.characterIds = characterIds == null ? null : characterIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    public void setGender(String gender) {
        this.gender = gender == null ? null : gender.trim().toUpperCase();
    }
}