package com.talentlens.user;

import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull(message = "role is required") Role role) {
}
