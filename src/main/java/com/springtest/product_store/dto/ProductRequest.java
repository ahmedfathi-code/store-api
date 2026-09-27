package com.springtest.product_store.dto;

import  jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductRequest {
    @NotBlank (message = "اسم المنتج مطلوب ")
    @Size(min=2 , max = 100 , message = "الاسم لازم يكون بين 2 و 100")
    private String name;

    @NotNull (message = "السعر مطلوب ")
    @Positive (message = " السعر لازم يكون اكبر من صفر ")
    private Double price;

    @NotBlank(message = " الكاتيجوري مطلوبه")
    private String category;

    @NotNull(message = "الكمية مطلوبة")
    @Min(value = 0, message = "الكمية مش ممكن تكون سالبة")
    private Integer stock;


}