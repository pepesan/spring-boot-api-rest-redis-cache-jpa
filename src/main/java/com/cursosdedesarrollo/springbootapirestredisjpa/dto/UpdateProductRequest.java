package com.cursosdedesarrollo.springbootapirestredisjpa.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Positive(message = "Price must be positive")
    private Double price;

    @PositiveOrZero(message = "Stock must be zero or positive")
    private Integer stock;
}
