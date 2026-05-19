package com.cursosdedesarrollo.springbootapirestredisjpa.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private String id;
    private String name;
    private String email;
    private String phone;
    private Integer loyaltyPoints;
}
