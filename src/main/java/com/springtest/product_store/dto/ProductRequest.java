package com.springtest.product_store.dto;

import  jakarta.validation.constraints.*;
import lombok.Data;

// Messages are keys in messages.properties / messages_ar.properties
@Data
public class ProductRequest {
    @NotBlank (message = "{validation.product.name.required}")
    @Size(min=2 , max = 100 , message = "{validation.product.name.size}")
    private String name;

    @NotNull (message = "{validation.product.price.required}")
    @Positive (message = "{validation.product.price.positive}")
    private Double price;

    @NotBlank(message = "{validation.product.category.required}")
    private String category;

    @NotNull(message = "{validation.product.stock.required}")
    @Min(value = 0, message = "{validation.product.stock.min}")
    private Integer stock;


}
