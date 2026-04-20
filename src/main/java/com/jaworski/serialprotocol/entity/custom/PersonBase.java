package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Common base class for person-like entities.
 * Subclasses must override column names via {@code @AttributeOverride}.
 */
@MappedSuperclass
@Data
@NoArgsConstructor
public abstract class PersonBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "surname", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String surname;

    @Column(name = "notes", nullable = true, length = 1000)
    @Size(max = 1000)
    private String notes;

    @Column(name = "nickname", nullable = true, length = 100)
    @Size(max = 100)
    private String nickname;

    @Column(name = "email", nullable = true, length = 100)
    @Size(max = 100)
    @Email
    private String email;

    @Column(name = "phone_number", nullable = true, length = 26)
    @Size(max = 26)
    private String phoneNumber;

    @Column(name = "address", nullable = true, length = 300)
    @Size(max = 300)
    private String address;
}

