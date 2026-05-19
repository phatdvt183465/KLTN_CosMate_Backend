package com.cosmate.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import com.cosmate.dto.response.ProviderCancellationPolicyResponse;

import com.cosmate.base.crud.dto.CrudDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse implements CrudDto<Integer> {
    private Integer id;
    private Integer userId;
    private String shopName;
    private Integer shopAddressId;
    private String avatarUrl;
    private String coverImageUrl;
    private String bio;
    private String bankAccountNumber;
    private String bankName;
    private Boolean verified;

    private Integer completedOrders;
    private BigDecimal totalRating;
    private Integer totalReviews;
    private List<ProviderCancellationPolicyResponse> cancellationPolicies;
}
