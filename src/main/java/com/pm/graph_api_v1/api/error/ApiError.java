package com.pm.graph_api_v1.api.error;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldErrorDto> fields
) {}
