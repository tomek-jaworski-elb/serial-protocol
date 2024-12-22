package com.jaworski.serialprotocol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Date;

@Entity
@Table(name = Student.TABLE_NAME)
@Data
public class Student {

    public static final String TABLE_NAME = "student";

    @Id
    @Column(name = TABLE_NAME + "_id")
    private int id;

    @Column(name = TABLE_NAME + "_name")
    private String name;

    @Column(name = TABLE_NAME + "_last_name")
    private String lastName;

    @Column(name = TABLE_NAME + "_course_no")
    private String courseNo;

    @Column(name = TABLE_NAME + "_date_begine")
    private Date dateBegine;

    @Column(name = TABLE_NAME + "_date_end")
    private Date dateEnd;

    @Column(name = TABLE_NAME + "_mr_ms")
    private String mrMs;

    @Column(name = TABLE_NAME + "_cert_type")
    private String certType;
}
