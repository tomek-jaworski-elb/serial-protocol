package com.jaworski.serialprotocol;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class PredicateTest {

    @Test
    void predicateNullTest() {
        Predicate<Integer> lesserThan = i -> (i < 18);
        Assertions.assertThrows(NullPointerException.class, () -> lesserThan.test(null));
    }

    @Test
    void predicateNotNullTest() {
        Predicate<Integer> lesserThan = i -> (i < 18);
        boolean test = lesserThan.test(3);
        Assertions.assertTrue(test);
    }


}
