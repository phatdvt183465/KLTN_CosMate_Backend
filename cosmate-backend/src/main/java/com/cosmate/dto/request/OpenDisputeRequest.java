package com.cosmate.dto.request;

import java.util.List;

public class OpenDisputeRequest {
    private String reason;
    private List<String> images;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}

