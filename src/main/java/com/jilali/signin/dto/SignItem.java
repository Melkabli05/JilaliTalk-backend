package com.jilali.signin.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SignItem(
 int sign_day,
    int gift_id,
    String gift_info,
    int gift_type,
    int gift_number,
    int sign_status,
    boolean to_day,
    String gift_name,
    String thumb
) {}
