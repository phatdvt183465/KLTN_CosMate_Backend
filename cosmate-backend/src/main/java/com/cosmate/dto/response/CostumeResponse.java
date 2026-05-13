package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

import com.cosmate.base.crud.dto.CrudDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostumeResponse implements CrudDto<Integer> {
    private Integer id;
    private String name;
    private String description;
    private String size;
    private String rentPurpose;
    private Integer numberOfItems;
    private BigDecimal pricePerDay;
    private BigDecimal depositAmount;
    private Integer rentDiscount;
    private String status;
    private Integer providerId;
    private Integer completedRentCount;
    private List<String> imageUrls;
    private List<MediaResponse> medias;
    private List<SurchargeResponse> surcharges;
    private List<AccessoryResponse> accessories;
    private List<RentalOptionResponse> rentalOptions;
    private List<CharacterDto> characters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SurchargeResponse {
        private Integer id;
        private String name;
        private String description;
        private BigDecimal price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessoryResponse {
        private Integer id;
        private String name;
        private String description;
        private BigDecimal price;
        private Boolean isRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RentalOptionResponse {
        private Integer id;
        private String name;
        private BigDecimal price;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterDto {
        private Integer id;
        private String name;
        private String anime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaResponse {
        private String url;
        private String type; // MAIN / DETAIL
        private String mediaType; // IMAGE / VIDEO
    }
}