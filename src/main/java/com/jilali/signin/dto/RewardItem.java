package com.jilali.signin.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RewardItem(
    int id,
    int gift_id,
    int type,
    int card_type,
    String name,
    int number,
    String icon,
    String multi_name
) {}
