package com.pm.graph_api_v1.api.error;

public record FieldErrorDto(
        String field,
        String message
) {}
