package com.skatehub.pojo.place;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlaceContributionRequest {

    @NotBlank(message = "Place name cannot be blank")
    @Size(max = 100, message = "Place name must be at most 100 characters")
    private String name;

    @Size(max = 200, message = "Place address must be at most 200 characters")
    private String address;

    @Size(max = 2000, message = "Place intro must be at most 2000 characters")
    private String intro;
}
