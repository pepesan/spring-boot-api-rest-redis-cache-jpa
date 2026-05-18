package com.cursosdedesarrollo.springbootapirestredisjpa.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
}
