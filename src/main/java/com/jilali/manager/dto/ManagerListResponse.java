package com.jilali.manager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ManagerListResponse(@JsonProperty("manager_list") @Nullable List<Manager> managerList) {
    public List<Manager> managerList() {
        return managerList == null ? List.of() : managerList;
    }
}
