package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.AppError;
import bd.ac.kuet.campuscycle.domain.AvailabilityStatus;
import bd.ac.kuet.campuscycle.domain.CampusHubs;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.CycleCondition;
import bd.ac.kuet.campuscycle.domain.CycleItem;
import bd.ac.kuet.campuscycle.domain.CycleType;
import bd.ac.kuet.campuscycle.domain.RentalRecord;
import bd.ac.kuet.campuscycle.domain.RentalStatus;
import bd.ac.kuet.campuscycle.domain.ReviewStatus;
import bd.ac.kuet.campuscycle.domain.SupportConversation;
import bd.ac.kuet.campuscycle.domain.SupportMessage;
import bd.ac.kuet.campuscycle.domain.TariffService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live Supabase repository via PostgREST RPCs only (no JDBC secrets).
 * Reads fall back to local demo data when offline; writes throw AppError.
 */
public final class SupabaseRpcRepository implements CampusRepository {
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private final InMemoryCampusRepository fallback = new InMemoryCampusRepository();

    @Override
    public List<CycleItem> catalog(CampusUser user) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.catalog(user);
        }
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("public_cycle_catalog", Map.of());
            List<CycleItem> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String ownerId = SupabaseRpcClient.str(r, "owner_id", "system");
                if (user != null && ownerId.equals(user.id())) continue;
                out.add(new CycleItem(
                        SupabaseRpcClient.str(r, "cycle_id", UUID.randomUUID().toString()),
                        ownerId, "KUET Member",
                        SupabaseRpcClient.str(r, "label", "Campus Cycle"),
                        parseType(SupabaseRpcClient.str(r, "cycle_type", "CITY_BIKE")),
                        parseCond(SupabaseRpcClient.str(r, "physical_condition", "GOOD")),
                        SupabaseRpcClient.str(r, "pickup_point", CampusHubs.names().get(0)),
                        SupabaseRpcClient.dbl(r, "latitude", 22.9009),
                        SupabaseRpcClient.dbl(r, "longitude", 89.5016),
                        SupabaseRpcClient.str(r, "description", ""),
                        ReviewStatus.APPROVED, AvailabilityStatus.AVAILABLE));
            }
            return out.isEmpty() ? fallback.catalog(user) : out;
        } catch (AppError e) {
            return fallback.catalog(user);
        }
    }

    @Override
    public List<CycleItem> pendingCycles() {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) return fallback.pendingCycles();
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("pending_cycle_reviews", Map.of());
            List<CycleItem> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new CycleItem(
                        SupabaseRpcClient.str(r, "cycle_id", UUID.randomUUID().toString()),
                        "system", "KUET Member",
                        SupabaseRpcClient.str(r, "label", "Campus Cycle"),
                        parseType(SupabaseRpcClient.str(r, "cycle_type", "CITY_BIKE")),
                        parseCond(SupabaseRpcClient.str(r, "physical_condition", "GOOD")),
                        SupabaseRpcClient.str(r, "pickup_point", CampusHubs.names().get(0)),
                        22.9009, 89.5016,
                        SupabaseRpcClient.str(r, "description", ""),
                        ReviewStatus.PENDING_REVIEW, AvailabilityStatus.AVAILABLE));
            }
            return out;
        } catch (AppError e) {
            return fallback.pendingCycles();
        }
    }

    @Override
    public List<RentalRecord> rentals(CampusUser user) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) return fallback.rentals(user);
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("my_rental_history", Map.of());
            List<RentalRecord> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                ZonedDateTime start = parseTime(SupabaseRpcClient.str(r, "started_at", null));
                ZonedDateTime ret = parseTimeOrNull(SupabaseRpcClient.str(r, "returned_at", ""));
                int mins = SupabaseRpcClient.num(r, "requested_minutes", 30);
                out.add(new RentalRecord(
                        SupabaseRpcClient.str(r, "rental_id", UUID.randomUUID().toString()),
                        SupabaseRpcClient.str(r, "rental_id", UUID.randomUUID().toString()),
                        SupabaseRpcClient.str(r, "cycle_label", "Campus Cycle"),
                        user.id(), mins,
                        SupabaseRpcClient.num(r, "quoted_amount_poisha", TariffService.quotePoisha(30)),
                        parseRental(SupabaseRpcClient.str(r, "state", "ACTIVE")),
                        start, start.plusMinutes(mins), ret));
            }
            return out.isEmpty() ? fallback.rentals(user) : out;
        } catch (AppError e) {
            return fallback.rentals(user);
        }
    }

    @Override
    public RentalRecord activeRental(CampusUser user) {
        return rentals(user).stream().filter(r -> r.status() == RentalStatus.ACTIVE).findFirst().orElse(null);
    }

    @Override
    public RentalRecord book(CampusUser renter, String cycleId, int minutes) {
        TariffService.quotePoisha(minutes);
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            throw new AppError("OFFLINE", "Booking needs sign-in and live store.");
        }
        try {
            UUID idem = UUID.randomUUID();
            List<Map<String, Object>> rows = SupabaseRpcClient.call("start_rental",
                    Map.of("p_cycle_id", cycleId, "p_requested_minutes", minutes, "p_idempotency_key", idem.toString()));
            if (rows.isEmpty()) throw new AppError("BOOK_FAILED", "Booking failed. Please retry.");
            Map<String, Object> row = rows.get(0);
            ZonedDateTime now = ZonedDateTime.now(DHAKA);
            return new RentalRecord(
                    SupabaseRpcClient.str(row, "rental_id", UUID.randomUUID().toString()),
                    cycleId, "Campus Cycle", renter.id(), minutes,
                    SupabaseRpcClient.num(row, "quoted_amount_poisha", TariffService.quotePoisha(minutes)),
                    RentalStatus.ACTIVE, now, now.plusMinutes(minutes), null);
        } catch (AppError e) {
            throw e;
        }
    }

    @Override
    public void returnRental(CampusUser renter, String rentalId) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            throw new AppError("OFFLINE", "Return needs sign-in and live store.");
        }
        SupabaseRpcClient.call("return_rental", Map.of("p_rental_id", rentalId));
    }

    @Override
    public void reviewCycle(CampusUser admin, String cycleId, boolean approved, String reason) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            throw new AppError("OFFLINE", "Review needs sign-in and live store.");
        }
        SupabaseRpcClient.call("review_cycle",
                Map.of("p_cycle_id", cycleId, "p_approve", approved, "p_reason", reason == null ? "" : reason));
    }

    @Override
    public void rebalanceHub(String sourceHub, String targetHub, int count) {
        throw new AppError("UNSUPPORTED", "Rebalance is a local demo action only.");
    }

    @Override
    public String openDispute(CampusUser renter, String rentalId, String reason) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.openDispute(renter, rentalId, reason);
        }
        List<Map<String, Object>> rows = SupabaseRpcClient.call("open_dispute",
                Map.of("p_rental_id", rentalId, "p_reason", reason.trim()));
        if (!rows.isEmpty()) {
            Object id = rows.get(0).values().iterator().next();
            if (id != null) return String.valueOf(id);
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public String createSupportConversation(CampusUser student, String subject, String message) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.createSupportConversation(student, subject, message);
        }
        List<Map<String, Object>> rows = SupabaseRpcClient.call("create_support_conversation",
                Map.of("p_subject", subject.trim(), "p_message", message.trim()));
        if (!rows.isEmpty()) {
            Object id = rows.get(0).values().iterator().next();
            if (id != null) return String.valueOf(id);
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public List<SupportConversation> supportConversations(CampusUser user) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.supportConversations(user);
        }
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("my_support_conversations", Map.of());
            List<SupportConversation> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new SupportConversation(
                        SupabaseRpcClient.str(r, "conversation_id", UUID.randomUUID().toString()),
                        SupabaseRpcClient.str(r, "subject", "Support"),
                        SupabaseRpcClient.str(r, "state", "OPEN"),
                        SupabaseRpcClient.str(r, "assigned_admin_name", ""),
                        ZonedDateTime.now(DHAKA)));
            }
            return out;
        } catch (AppError e) {
            return fallback.supportConversations(user);
        }
    }

    @Override
    public List<SupportMessage> supportMessages(CampusUser user, String conversationId) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.supportMessages(user, conversationId);
        }
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("support_message_history",
                    Map.of("p_conversation_id", conversationId));
            List<SupportMessage> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new SupportMessage(
                        SupabaseRpcClient.str(r, "message_id", UUID.randomUUID().toString()),
                        SupabaseRpcClient.str(r, "sender_name", "Member"),
                        SupabaseRpcClient.str(r, "sender_role", "STUDENT"),
                        SupabaseRpcClient.str(r, "body", ""),
                        ZonedDateTime.now(DHAKA)));
            }
            return out;
        } catch (AppError e) {
            return fallback.supportMessages(user, conversationId);
        }
    }

    @Override
    public void postSupportMessage(CampusUser user, String conversationId, String body) {
        if (body == null || body.trim().isEmpty() || body.trim().length() > 2000) {
            throw new IllegalArgumentException("Message must be 1-2000 characters.");
        }
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            fallback.postSupportMessage(user, conversationId, body);
            return;
        }
        SupabaseRpcClient.call("post_support_message",
                Map.of("p_conversation_id", conversationId, "p_body", body.trim()));
    }

    @Override
    public List<bd.ac.kuet.campuscycle.domain.DisputeItem> disputeQueue(CampusUser admin) {
        if (!SupabaseRpcClient.isConfigured() || !SessionStore.hasSession()) {
            return fallback.disputeQueue(admin);
        }
        try {
            List<Map<String, Object>> rows = SupabaseRpcClient.call("dispute_queue", Map.of());
            List<bd.ac.kuet.campuscycle.domain.DisputeItem> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new bd.ac.kuet.campuscycle.domain.DisputeItem(
                        SupabaseRpcClient.str(r, "dispute_id", UUID.randomUUID().toString()),
                        SupabaseRpcClient.str(r, "rental_id", ""),
                        SupabaseRpcClient.str(r, "reason", ""),
                        SupabaseRpcClient.str(r, "state", "OPEN")));
            }
            return out;
        } catch (AppError e) {
            return fallback.disputeQueue(admin);
        }
    }

    private CycleType parseType(String v) {
        try {
            return CycleType.valueOf(v.toUpperCase());
        } catch (Exception e) {
            return CycleType.CITY_BIKE;
        }
    }

    private CycleCondition parseCond(String v) {
        try {
            return CycleCondition.valueOf(v.toUpperCase());
        } catch (Exception e) {
            return CycleCondition.GOOD;
        }
    }

    private RentalStatus parseRental(String v) {
        try {
            return RentalStatus.valueOf(v.toUpperCase());
        } catch (Exception e) {
            return RentalStatus.ACTIVE;
        }
    }

    private ZonedDateTime parseTime(String v) {
        try {
            return ZonedDateTime.parse(v);
        } catch (Exception e) {
            return ZonedDateTime.now(DHAKA);
        }
    }

    private ZonedDateTime parseTimeOrNull(String v) {
        try {
            if (v == null || v.isBlank()) return null;
            return ZonedDateTime.parse(v);
        } catch (Exception e) {
            return null;
        }
    }
}
