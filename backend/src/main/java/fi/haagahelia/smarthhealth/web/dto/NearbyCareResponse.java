package fi.haagahelia.smarthhealth.web.dto;

public record NearbyCareResponse(
        String id,
        String name,
        String address,
        String phoneNumber,
        double latitude,
        double longitude,
        String mapUrl,
        Double rating) {}