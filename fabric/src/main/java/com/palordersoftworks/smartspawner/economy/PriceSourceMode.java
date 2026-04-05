package com.palordersoftworks.smartspawner.economy;

import java.util.Locale;

public enum PriceSourceMode {
    SHOP_ONLY,
    SHOP_PRIORITY,
    CUSTOM_ONLY,
    CUSTOM_PRIORITY;

    public static PriceSourceMode fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return SHOP_PRIORITY;
        }
        try {
            return PriceSourceMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SHOP_PRIORITY;
        }
    }
}
