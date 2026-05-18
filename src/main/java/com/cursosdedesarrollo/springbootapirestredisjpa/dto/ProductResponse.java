package com.cursosdedesarrollo.springbootapirestredisjpa.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
}
