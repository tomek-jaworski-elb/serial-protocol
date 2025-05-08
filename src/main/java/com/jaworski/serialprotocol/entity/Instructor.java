package com.jaworski.serialprotocol.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "instructor")
public class Instructor {

    @Id
    @Column(name = "id")
    @ManyToMany(mappedBy = "instructors", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private int id;

    @Column(name = "name")
    private String name;

    @Column(name = "last_name")
    private String lastName;

}
