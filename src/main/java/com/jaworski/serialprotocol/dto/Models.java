package com.jaworski.serialprotocol.dto;

import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public enum Models {

    WARTA(1, "Warta"),
    BLUE_LADY(2, "Blue Lady"),
    LADY_MARIE(6, "Lady Marie"),
    CHERRY_LADY(4, "Cherry Lady"),
    KOLOBRZEG(5, "Kołobrzeg"),
    DORCHERTER_LADY(3, "Dorchester Lady");

    private final int id;
    private final String name;

    Models(int id, String name) {
        this.id = id;
        this.name = name;
    }

    private static final Map<Integer, Models> MODEL_MAP = Arrays.stream(Models.values())
            .collect(Collectors.toUnmodifiableMap(Models::getId, models -> models));

    @Nullable
    public static Models fromId(int id) {
        return MODEL_MAP.get(id);
    }
}
