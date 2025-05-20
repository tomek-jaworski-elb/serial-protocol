package com.jaworski.serialprotocol.service.db;

import com.jaworski.serialprotocol.entity.Staff;
import com.jaworski.serialprotocol.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;

    public void save(Staff staff) {
        staffRepository.save(staff);
    }

    public Staff findStaffById(int id) {
        return staffRepository.findById(id).orElse(null);
    }
}
