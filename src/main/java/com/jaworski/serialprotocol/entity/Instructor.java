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

import java.time.LocalDate;

@Entity
@Table(name = Instructor.TABLE_NAME)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Instructor {

    public static final String TABLE_NAME = "instructor";

    @Id
    @Column(name = TABLE_NAME + "_no")
    private int no;

    @Column(name = TABLE_NAME + "_name")
    private String name;

    @Column(name = TABLE_NAME + "_surname")
    private String surname;

    @Column(name = TABLE_NAME + "_email")
    private String email;

    @Column(name = TABLE_NAME + "_phone")
    private String phone;

    @Column(name = TABLE_NAME + "_mobile")
    private String mobile;

    @Column(name = TABLE_NAME + "_city")
    private String city;

    @Column(name = TABLE_NAME + "_address")
    private String address;

    @Column(name = TABLE_NAME + "_postcode")
    private String postcode;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = TABLE_NAME + "_photo1", columnDefinition = "LONGBLOB")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] photo1;

    @Lob
    @Column(name = TABLE_NAME + "_photo2", columnDefinition = "LONGBLOB")
    @Basic(fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] photo2;

    @Lob
    @Column(name = TABLE_NAME + "_photo3", columnDefinition = "LONGBLOB")
    @Basic(fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] photo3;

    @Lob
    @Column(name = TABLE_NAME + "_photo4", columnDefinition = "LONGBLOB")
    @Basic(fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private byte[] photo4;

    @Column(name = TABLE_NAME + "_notes")
    private String notes;

    @Column(name = TABLE_NAME + "_other_notes")
    private String otherNotes;

    @Column(name = TABLE_NAME + "_cert_no")
    private String certNo;

    @Column(name = TABLE_NAME + "_specialization")
    private String specialization;

    @Column(name = TABLE_NAME + "_diploma")
    private String diploma;

    @Column(name = TABLE_NAME + "_birth_date")
    private LocalDate birthDate;

    @Column(name = TABLE_NAME + "_birth_place")
    private String birthPlace;

    @Column(name = TABLE_NAME + "_mr_ms")
    private String mrMs;

    @Column(name = TABLE_NAME + "_nick")
    private String nick;

    @Column(name = TABLE_NAME + "_no_certificate")
    private String noCertificate;

    @Column(name = TABLE_NAME + "_expiration_date")
    private LocalDate expirationDate;
}
