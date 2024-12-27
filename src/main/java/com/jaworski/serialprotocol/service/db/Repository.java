package com.jaworski.serialprotocol.service.db;

import java.util.Collection;

public interface Repository <T> {

    T save(T student);

    T getStudentById(int id);

    Collection<T> getAll();
}
