package hu.familyfleet.vehicle.dto;

/** Magyar rendszámformátumok a request-DTO-k validációjához. */
final class LicensePlates {

    /**
     * Régi formátum: 3 betű + 3 szám (ABC-123), új (2022-től): 2+2 betű + 3 szám (AA-BC-123).
     * Elválasztó lehet kötőjel, szóköz vagy semmi; kis- és nagybetű egyaránt elfogadott,
     * a tárolt alak normalizált (lásd {@code Vehicle.normalizePlate}).
     */
    static final String PATTERN = "^(?:[A-Za-z]{3}[ -]?[0-9]{3}|[A-Za-z]{2}[ -]?[A-Za-z]{2}[ -]?[0-9]{3})$";

    static final String MESSAGE = "érvénytelen rendszám, elfogadott formátum pl. ABC-123 vagy AA-BC-123";

    private LicensePlates() {}
}
