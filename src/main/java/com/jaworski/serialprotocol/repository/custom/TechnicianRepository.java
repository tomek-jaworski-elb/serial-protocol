package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Technician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TechnicianRepository extends JpaRepository<Technician, UUID> {
}

