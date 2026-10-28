package hu.familyfleet.deadline.dto;

import java.time.LocalDate;

/**
 * Teljesítés (a törzs elhagyható). A {@code nextDueDate} felülírja az automatikus ismétlődést,
 * és nem ismétlődő típusnál (pl. jogosítvány) is létrehoz egy következő határidőt.
 */
public record CompleteDeadlineRequest(LocalDate nextDueDate) {}
