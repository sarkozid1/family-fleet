package hu.familyfleet.deadline.dto;

/** A teljesített határidő és – ismétlődő típusnál – az újonnan létrehozott következő (különben {@code null}). */
public record CompleteDeadlineResponse(DeadlineResponse completed, DeadlineResponse next) {}
