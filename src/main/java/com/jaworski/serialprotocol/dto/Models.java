package com.jaworski.serialprotocol.dto;

import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public enum Models {

    WARTA(1, "Warta", "orange"),
    BLUE_LADY(2, "Blue Lady", "blue"),
    CHERRY_LADY(4, "Cherry Lady", "purple"),
    DORCHERTER_LADY(3, "Dorchester Lady", "green"),
    LADY_MARIE(6, "Lady Marie", "darkblue"),
    KOLOBRZEG(5, "Kołobrzeg", "lightgray");

    private final int id;
    private final String name;
    private final String color;

    Models(int id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    private static final Map<Integer, Models> MODEL_MAP = Arrays.stream(Models.values())
            .collect(Collectors.toUnmodifiableMap(Models::getId, models -> models));

    @Nullable
    public static Models fromId(int id) {
        return MODEL_MAP.get(id);
    }
}
