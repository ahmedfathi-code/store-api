package com.springtest.product_store.controller;

// Paging bounds and their validation messages, shared by every paginated endpoint
// (annotation values must be compile-time constants)
final class PagingLimits {

    static final int MIN_PAGE = 0;
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    static final String PAGE_MESSAGE = "page must be 0 or greater";
    static final String MIN_SIZE_MESSAGE = "size must be 1 or greater";
    static final String MAX_SIZE_MESSAGE = "size must be at most 100";

    private PagingLimits() {
    }
}
