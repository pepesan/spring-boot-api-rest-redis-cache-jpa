package com.cursosdedesarrollo.springbootapirestredisjpa.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    private String id;
    private String name;
    private String email;
    private String phone;
    private Integer loyaltyPoints;
}
