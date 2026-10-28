package hu.familyfleet.insurance.dto;

import hu.familyfleet.insurance.Insurer;

public record InsurerResponse(Long id, String name, String phone, String website) {

    public static InsurerResponse from(Insurer insurer) {
        return new InsurerResponse(insurer.getId(), insurer.getName(), insurer.getPhone(), insurer.getWebsite());
    }
}
