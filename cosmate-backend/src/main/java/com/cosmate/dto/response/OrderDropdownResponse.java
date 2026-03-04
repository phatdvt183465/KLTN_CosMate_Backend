package com.cosmate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDropdownResponse {
    private Integer id;
    private String label; // e.g., "#123 - 2026-03-04"
}

