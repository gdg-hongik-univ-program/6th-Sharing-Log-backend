package gdg.sharinglog.web.booking.dto;

public record ReservationMemberResponse(
        String membershipId,
        String email,
        boolean me
) {
}
