package com.jaworski.serialprotocol.mappers;

import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.entity.Instructor;

import java.time.ZoneId;
import java.util.Base64;
import java.util.Objects;

public class InstructorMapper {

    public static Instructor mapToEntity(InstructorDto instructorDto) {
        Instructor instructor = new Instructor();
        if (instructorDto != null) {
            instructor.setNo(instructorDto.getNo());
            instructor.setName(instructorDto.getName());
            instructor.setSurname(instructorDto.getSurname());
            instructor.setEmail(instructorDto.getEmail());
            instructor.setPhone(instructorDto.getPhone());
            instructor.setMobile(instructorDto.getMobile());
            instructor.setCity(instructorDto.getCity());
            instructor.setAddress(instructorDto.getAddress());
            instructor.setPostcode(instructorDto.getPostcode());
            instructor.setPhoto1(decode(instructorDto.getPhoto1()));
            instructor.setPhoto2(decode(instructorDto.getPhoto2()));
            instructor.setPhoto3(decode(instructorDto.getPhoto3()));
            instructor.setPhoto4(decode(instructorDto.getPhoto4()));
            instructor.setNotes(instructorDto.getNotes());
            instructor.setOtherNotes(instructorDto.getOtherNotes());
            instructor.setCertNo(instructorDto.getCertNo());
            instructor.setSpecialization(instructorDto.getSpecialization());
            instructor.setDiploma(instructorDto.getDiploma());
            instructor.setBirthDate(instructorDto.getBirthDate() == null
                    ? null
                    : instructorDto.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            instructor.setBirthPlace(instructorDto.getBirthPlace());
            instructor.setMrMs(instructorDto.getMrMs());
            instructor.setNick(instructorDto.getNick());
            instructor.setNoCertificate(instructorDto.getNoCertificate());
            instructor.setExpirationDate(instructorDto.getExpirationDate() == null
                    ? null
                    : instructorDto.getExpirationDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        return instructor;
    }

    private InstructorMapper() {
    }

    private static byte[] decode(String photo) {
        if (photo == null) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(photo);
    }

    public static InstructorDto mapToDto(Instructor instructor) {

        InstructorDto instructorDto = new InstructorDto();
        if (instructor != null) {
            instructorDto.setNo(instructor.getNo());
            instructorDto.setName(instructor.getName());
            instructorDto.setSurname(instructor.getSurname());
            instructorDto.setNick(instructor.getNick());
            instructorDto.setPhoto1(encode(instructor.getPhoto1()));
            instructorDto.setPhoto2(encode(instructor.getPhoto2()));
            instructorDto.setPhoto3(encode(instructor.getPhoto3()));
            instructorDto.setPhoto4(encode(instructor.getPhoto4()));
        }
        return instructorDto;
    }

    private static String encode(byte[] photo) {
        return Base64.getEncoder().encodeToString(Objects.requireNonNullElseGet(photo, () -> new byte[0]));
    }
}
