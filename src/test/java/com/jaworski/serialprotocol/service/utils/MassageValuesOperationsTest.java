package com.jaworski.serialprotocol.service.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class MassageValuesOperationsTest {

    @Autowired
    private MassageValuesOperations massageValuesOperations;

    @Test
    void autowired_NotNull() {
        assertNotNull(massageValuesOperations);
    }

    @Test
    void headingAlignment_value_equals_360() {
        double heading = 360;
        double headingAlignment = massageValuesOperations.headingAlignment(heading);
        assertEquals(0, headingAlignment);
    }


    @Test
    void headingAlignment_value_less_than_360() {
        double heading = 360;
        double headingAlignment = massageValuesOperations.headingAlignment(heading);
        assertEquals(0, headingAlignment);
    }

    @Test
    void headingAlignment_value_greater_than_360() {
        double heading = 370;
        double headingAlignment = massageValuesOperations.headingAlignment(heading);
        assertEquals(10, headingAlignment);
    }

    @Test
    void headingAlignment_value_greater_than_360_() {
        double heading = 370.05;
        double headingAlignment = massageValuesOperations.headingAlignment(heading);
        assertEquals(10.05, headingAlignment, 0.01);
    }
}