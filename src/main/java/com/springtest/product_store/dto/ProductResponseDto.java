package com.springtest.product_store.dto;

// The only product shape returned by the API (list, get, search, create, update)
public class ProductResponseDto {
    private Long id;
    private String name;
    private double price;
    private String category;
    private Integer stock;


    public ProductResponseDto() {
    }

    public ProductResponseDto(Long id, String name, double price, String category, Integer stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.stock = stock;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

}
