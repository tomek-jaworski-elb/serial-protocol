package com.jaworski.serialprotocol.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@ToString
@Setter
@Getter
public class CheckBoxOption {

    private boolean warta;
    private boolean bluelady;
    private boolean ladymarie;
    private boolean cherrylady;
    private boolean kolobrzeg;
    private boolean dorchesterlady;
    private String minValue;
    private String maxValue;

    public Set<Integer> getModels() {
        Set<Integer> models = new HashSet<>();
        if (this.warta) {
            models.add(Models.WARTA.getId());
        }
        if (this.bluelady) {
            models.add(Models.BLUE_LADY.getId());
        }
        if (this.cherrylady) {
            models.add(Models.CHERRY_LADY.getId());
        }
        if (this.ladymarie) {
            models.add(Models.LADY_MARIE.getId());
        }
        if (this.kolobrzeg) {
            models.add(Models.KOLOBRZEG.getId());
        }
        if (this.dorchesterlady) {
            models.add(Models.DORCHERTER_LADY.getId());
        }
        return models;
    }

}
