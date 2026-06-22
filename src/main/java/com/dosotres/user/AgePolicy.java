package com.dosotres.user;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/** Política de edad: centraliza el umbral de adultez para el gate de vínculo entre desconocidos. */
public final class AgePolicy {

    public static final int ADULT_AGE = 18;

    private AgePolicy() {
    }

    /** Adulto = fecha de nacimiento declarada y edad ≥ 18 al día de hoy. */
    public static boolean isAdult(LocalDate dateOfBirth, Clock clock) {
        if (dateOfBirth == null) {
            return false;
        }
        return Period.between(dateOfBirth, LocalDate.now(clock)).getYears() >= ADULT_AGE;
    }
}
