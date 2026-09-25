package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.RoutePoint;

import java.util.ArrayList;
import java.util.List;

/** Decodes the encoded polyline format returned by ORS-compatible routing APIs. */
final class PolylineDecoder {
    private PolylineDecoder() { }

    static List<RoutePoint> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Route geometry is missing");
        List<RoutePoint> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;
        while (index < encoded.length()) {
            int[] latitudePart = decodeNumber(encoded, index);
            latitude += latitudePart[0];
            index = latitudePart[1];
            int[] longitudePart = decodeNumber(encoded, index);
            longitude += longitudePart[0];
            index = longitudePart[1];
            points.add(new RoutePoint(latitude / 1E5, longitude / 1E5));
        }
        if (points.size() < 2) throw new IllegalArgumentException("Route geometry is incomplete");
        return List.copyOf(points);
    }

    private static int[] decodeNumber(String encoded, int index) {
        int value = 0;
        int shift = 0;
        while (index < encoded.length()) {
            int chunk = encoded.charAt(index++) - 63;
            if (chunk < 0 || chunk > 63) throw new IllegalArgumentException("Route geometry is invalid");
            value |= (chunk & 31) << shift;
            shift += 5;
            if ((chunk & 32) == 0) return new int[] { ((value & 1) == 1 ? ~(value >> 1) : value >> 1), index };
            if (shift > 30) throw new IllegalArgumentException("Route geometry is invalid");
        }
        throw new IllegalArgumentException("Route geometry is incomplete");
    }
}
