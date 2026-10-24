package com.jaworski.serialprotocol.entity;

import com.jaworski.serialprotocol.entity.custom.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Lob;
import jakarta.persistence.Basic;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Date;

/**
 * Legacy record fed by the external REST client ({@code /api/v1/students}). Kept, so it carries
 * the same version, timestamps and tombstone as the custom domain. The {@code int} id is
 * assigned by the sending system and is therefore unique by origin, which is what a merge needs.
 */
@Entity
@Table(name = Student.TABLE_NAME)
@SQLRestriction("deleted_at is null")
@SQLDelete(sql = "UPDATE student SET deleted_at = CURRENT_TIMESTAMP(6) WHERE student_id = ? AND version = ?")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Student extends Auditable {

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
}
