package com.pharmaprice.recommendation.distance;

/**
 * 반경 검색을 위한 거리 계산 인터페이스. PostGIS 도입 시 이 인터페이스 뒤로 구현체만
 * 교체하면 되도록 호출부와 분리한다.
 */
public interface DistanceCalculator {

	BoundingBox boundingBox(double lat, double lng, int radiusM);

	double distanceMeters(double lat1, double lng1, double lat2, double lng2);

	record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
	}
}
