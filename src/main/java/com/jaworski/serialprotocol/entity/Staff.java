package com.jaworski.serialprotocol.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @Column(name = "id")
    @ManyToOne(fetch = FetchType.EAGER)
    private int id;

    @Column(name = "name")
    private String name;

    @Column(name = "surname")
    private String surname;
}
