package com.code.aigateway.admin.model.dataobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigDOTest {

    @Test
    @DisplayName("simplified → simplified")
    void simplified_returnsSimplified() {
        assertThat(ProviderConfigDO.normalizeThinkingCompatMode("simplified")).isEqualTo("simplified");
    }

    @Test
    @DisplayName("SIMPLIFIED（大写）→ simplified")
    void simplifiedUpperCase_returnsSimplified() {
        assertThat(ProviderConfigDO.normalizeThinkingCompatMode("SIMPLIFIED")).isEqualTo("simplified");
    }

    @Test
    @DisplayName("full → full")
    void full_returnsFull() {
        assertThat(ProviderConfigDO.normalizeThinkingCompatMode("full")).isEqualTo("full");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "enabled", "disabled", "FULL", " "})
    @DisplayName("非法值或空值默认返回 full")
    void invalidOrDefault_returnsFull(String input) {
        assertThat(ProviderConfigDO.normalizeThinkingCompatMode(input)).isEqualTo("full");
    }
}
