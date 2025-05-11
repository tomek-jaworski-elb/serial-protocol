package com.jaworski.serialprotocol.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;
import java.util.Set;

@Entity
@Table(name = Student.TABLE_NAME)
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = TABLE_NAME + "_photo", columnDefinition = "LONGBLOB")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] photo;

    @Column(name = TABLE_NAME + "_visible", nullable = false)
    private boolean visible;

    @Column(name = TABLE_NAME + "_instructors")
    private Set<Instructor> instructors;

    @Column(name = TABLE_NAME + "_staff")
    private Set<Staff> staff;
}
