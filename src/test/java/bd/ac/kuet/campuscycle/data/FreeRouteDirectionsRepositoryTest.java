package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CycleRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FreeRouteDirectionsRepositoryTest {
    @Test
    void parsesEncodedRouteGeometryAndSummary() {
        String response = """
                {"routes":[{"geometry":"_p~iF~ps|U_ulLnnqC_mqNvxq`@","summary":{"distance":1250.4,"duration":317.2}}]}
                """;

        CycleRoute route = FreeRouteDirectionsRepository.parseRoute("cycle-7", response);

        assertEquals("cycle-7", route.destinationCycleId());
        assertEquals(3, route.points().size());
        assertEquals(38.5, route.points().get(0).latitude(), 0.00001);
        assertEquals(-120.2, route.points().get(0).longitude(), 0.00001);
        assertEquals(1250, route.distanceMetres());
        assertEquals(317, route.durationSeconds());
    }
}
