package com.springtest.product_store.exception;

import org.springframework.context.MessageSourceResolvable;

// Carries a message key + arguments so the 404 can be translated per request;
// getMessage() keeps the English text for logs and tests
public class ResourceNotFoundException extends RuntimeException implements MessageSourceResolvable {

    private final String code;
    private final Object[] args;

    public ResourceNotFoundException(String code, String defaultMessage, Object... args) {
        super(defaultMessage);
        this.code = code;
        this.args = args;
    }

    public static ResourceNotFoundException product(Long id) {
        // id as text: MessageFormat would format a number with grouping (999,999) or locale digits
        return new ResourceNotFoundException("product.notFound", "Product not found with id: " + id, String.valueOf(id));
    }

    @Override
    public String[] getCodes() {
        return new String[]{code};
    }

    @Override
    public Object[] getArguments() {
        return args;
    }

    @Override
    public String getDefaultMessage() {
        return getMessage();
    }
}
