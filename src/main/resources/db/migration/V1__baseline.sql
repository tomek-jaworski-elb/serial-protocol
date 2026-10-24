-- Baseline of the schema as Hibernate 7.4 generates it for MariaDB 11.8 from the entity
-- mappings (jakarta.persistence.schema-generation.scripts.action=create).
--
-- Existing installations were built incrementally by ddl-auto=update and are marked as
-- version 1 without running this file (spring.flyway.baseline-on-migrate=true). A fresh
-- database runs it. Hibernate's ddl-auto=validate then checks every node against the
-- mappings on startup, so a drifted installation fails fast instead of silently diverging.
--
-- Constraint names are the ones Hibernate derives from the column names, so a baselined
-- installation and a fresh one carry identical names.

create table course_counter (
    course_counter_counter bigint not null,
    course_counter_uuid uuid not null,
    image_uuid uuid,
    primary key (course_counter_uuid)
) engine=InnoDB;

create table course_type (
    course_type_id bigint not null auto_increment,
    course_type_code varchar(32) not null,
    course_type_long_description varchar(5000),
    course_type_description varchar(255) not null,
    primary key (course_type_id)
) engine=InnoDB;

create table courses (
    courses_end_date date not null,
    courses_start_date date not null,
    courses_course_type_id bigint not null,
    courses_id bigint not null,
    course_counter_uuid uuid,
    courses_participant_id uuid not null,
    courses_uuid uuid not null,
    primary key (courses_uuid)
) engine=InnoDB;

create table courses_lecturers (
    courses_uuid uuid not null,
    lecturer_uuid uuid not null,
    primary key (courses_uuid, lecturer_uuid)
) engine=InnoDB;

create table courses_technicians (
    courses_uuid uuid not null,
    technician_uuid uuid not null,
    primary key (courses_uuid, technician_uuid)
) engine=InnoDB;

create table courses_trainers (
    courses_uuid uuid not null,
    trainer_uuid uuid not null,
    primary key (courses_uuid, trainer_uuid)
) engine=InnoDB;

create table image (
    image_thumb_version varchar(16),
    image_uuid uuid not null,
    image_content_type varchar(100) not null,
    image_thumb_content_type varchar(100),
    image_data LONGBLOB not null,
    image_thumb_data LONGBLOB,
    primary key (image_uuid)
) engine=InnoDB;

create table instructor (
    instructor_birth_date date,
    instructor_expiration_date date,
    instructor_no integer not null,
    instructor_address varchar(255),
    instructor_birth_place varchar(255),
    instructor_cert_no varchar(255),
    instructor_city varchar(255),
    instructor_diploma varchar(255),
    instructor_email varchar(255),
    instructor_mobile varchar(255),
    instructor_mr_ms varchar(255),
    instructor_name varchar(255),
    instructor_nick varchar(255),
    instructor_no_certificate varchar(255),
    instructor_notes varchar(255),
    instructor_other_notes varchar(255),
    instructor_phone varchar(255),
    instructor_postcode varchar(255),
    instructor_specialization varchar(255),
    instructor_surname varchar(255),
    instructor_photo1 LONGBLOB,
    instructor_photo2 LONGBLOB,
    instructor_photo3 LONGBLOB,
    instructor_photo4 LONGBLOB,
    primary key (instructor_no)
) engine=InnoDB;

create table lecturer (
    lecturer_primary_image_uuid uuid,
    lecturer_uuid uuid not null,
    lecturer_phone_number varchar(26),
    lecturer_email varchar(100),
    lecturer_name varchar(100) not null,
    lecturer_nickname varchar(100),
    lecturer_surname varchar(100) not null,
    lecturer_address varchar(300),
    lecturer_notes varchar(1000),
    primary key (lecturer_uuid)
) engine=InnoDB;

create table lecturer_image (
    image_uuid uuid not null,
    lecturer_uuid uuid not null,
    primary key (image_uuid, lecturer_uuid)
) engine=InnoDB;

create table participants (
    participants_birth_date date,
    participants_id bigint not null,
    image_uuid uuid,
    participants_uuid uuid not null,
    participants_phone_number varchar(26),
    participants_email varchar(100),
    participants_name varchar(100) not null,
    participants_nickname varchar(100),
    participants_surname varchar(100) not null,
    participants_address varchar(300),
    participants_notes varchar(1000),
    primary key (participants_uuid)
) engine=InnoDB;

create table student (
    student_id integer not null,
    student_date_begine datetime(6),
    student_date_end datetime(6),
    student_cert_type varchar(255),
    student_course_no varchar(255),
    student_last_name varchar(255),
    student_mr_ms varchar(255),
    student_name varchar(255),
    student_photo LONGBLOB,
    primary key (student_id)
) engine=InnoDB;

create table technician (
    technician_primary_image_uuid uuid,
    technician_uuid uuid not null,
    technician_phone_number varchar(26),
    technician_email varchar(100),
    technician_name varchar(100) not null,
    technician_nickname varchar(100),
    technician_surname varchar(100) not null,
    technician_address varchar(300),
    technician_notes varchar(1000),
    primary key (technician_uuid)
) engine=InnoDB;

create table technician_image (
    image_uuid uuid not null,
    technician_uuid uuid not null,
    primary key (image_uuid, technician_uuid)
) engine=InnoDB;

create table trainer (
    trainer_primary_image_uuid uuid,
    trainer_uuid uuid not null,
    trainer_phone_number varchar(26),
    trainer_email varchar(100),
    trainer_name varchar(100) not null,
    trainer_nickname varchar(100),
    trainer_surname varchar(100) not null,
    trainer_address varchar(300),
    trainer_notes varchar(1000),
    primary key (trainer_uuid)
) engine=InnoDB;

create table trainer_image (
    image_uuid uuid not null,
    trainer_uuid uuid not null,
    primary key (image_uuid, trainer_uuid)
) engine=InnoDB;

alter table if exists course_counter add constraint UK12kjt7dynxsetvce96hpe9idk unique (course_counter_counter);

alter table if exists course_counter add constraint UK4y2safg50p567hw8fba78kvt0 unique (image_uuid);

alter table if exists course_type add constraint UKbj2g8ioh7lx7n7n739lf4ckb9 unique (course_type_code);

alter table if exists participants add constraint UKsx8oi6rcuw7iuj4gcu1d8a5bv unique (participants_id);

alter table if exists participants add constraint UKl7s9kc4taj8ec8llr7tmkrm86 unique (image_uuid);

alter table if exists course_counter add constraint FKs2n38xmp14l8rx61iqglhfs53 foreign key (image_uuid) references image (image_uuid);

alter table if exists courses add constraint FKtcbt5w73pkdws31axb2gqcllh foreign key (course_counter_uuid) references course_counter (course_counter_uuid);

alter table if exists courses add constraint FK4gv924oowpglsih2wlrjaftdb foreign key (courses_course_type_id) references course_type (course_type_id);

alter table if exists courses add constraint FKjuua8ho8345t0d8ptv4oy6m0g foreign key (courses_participant_id) references participants (participants_uuid);

alter table if exists courses_lecturers add constraint FK1l78n019cgyyot0xf30gk336s foreign key (lecturer_uuid) references lecturer (lecturer_uuid);

alter table if exists courses_lecturers add constraint FKqfuf563dgiuxyueeluxwp1uce foreign key (courses_uuid) references courses (courses_uuid);

alter table if exists courses_technicians add constraint FKlmx99khc56gajmer2juwobyx9 foreign key (technician_uuid) references technician (technician_uuid);

alter table if exists courses_technicians add constraint FKh0lwpqy7rncjgrts5pn3pdp2s foreign key (courses_uuid) references courses (courses_uuid);

alter table if exists courses_trainers add constraint FKk0lsva75c3boy99mcy349kpgj foreign key (trainer_uuid) references trainer (trainer_uuid);

alter table if exists courses_trainers add constraint FKmqmsgcdxjgvsqsgnfsiaxlea1 foreign key (courses_uuid) references courses (courses_uuid);

alter table if exists lecturer_image add constraint FK24iuolx2pouliga34dqkk9ifa foreign key (image_uuid) references image (image_uuid);

alter table if exists lecturer_image add constraint FKdwpjiykm54gobvt1hmijgjp3d foreign key (lecturer_uuid) references lecturer (lecturer_uuid);

alter table if exists participants add constraint FKb0v35q8o3gvn0a2tn0kxrsrqc foreign key (image_uuid) references image (image_uuid);

alter table if exists technician_image add constraint FKst7uu6tvs6qyligwpw82ratp1 foreign key (image_uuid) references image (image_uuid);

alter table if exists technician_image add constraint FKs1po8d6hu658areewxbeorbwf foreign key (technician_uuid) references technician (technician_uuid);

alter table if exists trainer_image add constraint FK7c6ravywmv9leqj4ulphgw0lv foreign key (image_uuid) references image (image_uuid);

alter table if exists trainer_image add constraint FKjk23qilemlciumfas0lh8t3hg foreign key (trainer_uuid) references trainer (trainer_uuid);
