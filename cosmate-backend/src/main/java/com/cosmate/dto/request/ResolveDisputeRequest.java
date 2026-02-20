package com.cosmate.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ResolveDisputeRequest {
    @NotBlank(message = "result is required")
    private String result; // e.g. AWARD_PROVIDER, AWARD_COSPLAYER, NO_ACTION

    // One of penaltyAmount or penaltyPercent must be provided; validation enforced in controller/service
    private BigDecimal penaltyAmount;

    @DecimalMin(value = "0.0", inclusive = false, message = "penaltyPercent must be > 0")
    @DecimalMax(value = "100.0", message = "penaltyPercent cannot exceed 100")
    private BigDecimal penaltyPercent;

    private String notes;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public BigDecimal getPenaltyAmount() {
        return penaltyAmount;
    }

    public void setPenaltyAmount(BigDecimal penaltyAmount) {
        this.penaltyAmount = penaltyAmount;
    }

    public BigDecimal getPenaltyPercent() {
        return penaltyPercent;
    }

    public void setPenaltyPercent(BigDecimal penaltyPercent) {
        this.penaltyPercent = penaltyPercent;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

