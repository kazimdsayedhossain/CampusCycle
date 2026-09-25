package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.TariffService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Validates migrations match the Java tariff + schema without touching live DB. */
public class DatabaseMigrationRunnerTest {

    @Test
    void migrationsMatchDomainRules() throws Exception {
        Path core = Path.of("supabase/migrations/202609250001_campuscycle_core.sql");
        Path workflows = Path.of("supabase/migrations/202609250003_campuscycle_workflows.sql");
        Path live = Path.of("supabase/migrations/202609260001_live_alignment.sql");
        assertTrue(Files.isRegularFile(core), "core migration missing");
        assertTrue(Files.isRegularFile(workflows), "workflows migration missing");
        assertTrue(Files.isRegularFile(live), "live alignment migration missing");

        String workflowSql = Files.readString(workflows);
        String liveSql = Files.readString(live);
        assertTrue(workflowSql.contains("rate_cards"), "rate_cards table missing");
        assertTrue(liveSql.contains("start_rental"), "live start_rental RPC missing");
        assertTrue(liveSql.contains("return_rental"), "live return_rental RPC missing");
        assertTrue(liveSql.contains("open_dispute"), "live open_dispute RPC missing");
        assertTrue(liveSql.contains("map_cycle_locations"), "live map RPC missing");
        assertTrue(liveSql.contains("'UNPAID'"), "payments must stay UNPAID");

        // Pilot rate card v1: 15 / 2000 / 15 / 1000 / 180
        assertTrue(workflowSql.contains("values (1, 15, 2000, 15, 1000, 180"),
                "rate card v1 seed drifted");
        assertEquals(15, TariffService.BASE_MINUTES);
        assertEquals(2000, TariffService.BASE_CHARGE_POISHA);
        assertEquals(15, TariffService.EXTRA_BLOCK_MINUTES);
        assertEquals(1000, TariffService.EXTRA_BLOCK_CHARGE_POISHA);
        assertEquals(180, TariffService.MAX_MINUTES);
        assertEquals(2000, TariffService.quotePoisha(15));
        assertEquals(3000, TariffService.quotePoisha(30));

        String coreSql = Files.readString(core);
        assertTrue(coreSql.contains("owner_phone"), "owner_phone check missing");
        assertTrue(coreSql.contains("PENDING_REVIEW"), "review lifecycle missing");
        assertTrue(coreSql.contains("revoke all"), "RLS revoke missing");
    }
}
