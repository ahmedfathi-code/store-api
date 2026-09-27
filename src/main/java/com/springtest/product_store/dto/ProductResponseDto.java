package com.springtest.product_store.dto;

public class ProductResponseDto {
    private Long id;
    private String name;
    private double price;
    private String category;


    public ProductResponseDto() {
    }

    public ProductResponseDto(Long id,String name , double price, String category) {
        this.id=id;
        this.name = name;
        this.price = price;
        this.category = category;
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

}
