package com.jaworski.serialprotocol.repository.custom;

import com.jaworski.serialprotocol.entity.custom.Trainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainerRepository extends JpaRepository<Trainer, Long> {
}
