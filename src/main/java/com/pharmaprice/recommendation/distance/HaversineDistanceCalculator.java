package com.pharmaprice.recommendation.distance;

import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

	private static final double EARTH_RADIUS_M = 6_371_000;
	private static final double METERS_PER_DEGREE_LAT = 111_320.0;
	private static final Set<Integer> ALLOWED_RADII_M = Set.of(500, 1000, 2000, 5000);

	// 대한민국 좌표 범위 (제주·독도 포함 여유치)
	private static final double MIN_LAT = 33.0;
	private static final double MAX_LAT = 39.0;
	private static final double MIN_LNG = 124.0;
	private static final double MAX_LNG = 132.0;

	@Override
	public BoundingBox boundingBox(double lat, double lng, int radiusM) {
		validateRadius(radiusM);
		validateCoordinate(lat, lng);

		double latDelta = radiusM / METERS_PER_DEGREE_LAT;
		double lngDelta = radiusM / (METERS_PER_DEGREE_LAT * cos(toRadians(lat)));

		return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
	}

	@Override
	public double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = toRadians(lat2 - lat1);
		double dLng = toRadians(lng2 - lng1);
		double a = pow(sin(dLat / 2), 2)
				+ cos(toRadians(lat1)) * cos(toRadians(lat2)) * pow(sin(dLng / 2), 2);
		return EARTH_RADIUS_M * 2 * asin(sqrt(a));
	}

	private static void validateRadius(int radiusM) {
		if (!ALLOWED_RADII_M.contains(radiusM)) {
			throw new IllegalArgumentException("허용되지 않는 반경입니다: " + radiusM);
		}
	}

	public static void validateCoordinate(double lat, double lng) {
		if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
			throw new IllegalArgumentException(
					"대한민국 범위를 벗어난 좌표입니다: lat=" + lat + ", lng=" + lng);
		}
	}
}
