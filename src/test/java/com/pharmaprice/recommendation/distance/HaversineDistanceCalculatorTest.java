package com.pharmaprice.recommendation.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.withinPercentage;

import org.junit.jupiter.api.Test;

import com.pharmaprice.common.exception.InvalidCoordinateException;
import com.pharmaprice.common.exception.InvalidRadiusException;

class HaversineDistanceCalculatorTest {

	private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

	@Test
	void distanceMeters_강남역에서_역삼역까지_약850미터() {
		double gangnamLat = 37.4979, gangnamLng = 127.0276;
		double yeoksamLat = 37.5006, yeoksamLng = 127.0366;

		double distance = calculator.distanceMeters(gangnamLat, gangnamLng, yeoksamLat, yeoksamLng);

		assertThat(distance).isCloseTo(850.0, withinPercentage(1));
	}

	@Test
	void boundingBox_반경원을_사방으로_거의_포함한다() {
		// 111,320m/도 선형 근사와 Haversine 구면 계산 간에는 위도에 따라 최대 약 0.3%의
		// 오차가 생긴다. 바운딩 박스는 애초에 정확한 원이 아니라 1차 후보 필터일 뿐이며,
		// 정확한 포함 여부는 distanceMeters로 하는 2차 필터링이 보장한다(가이드 4번).
		double lat = 37.5665, lng = 126.9780; // 서울시청
		int radiusM = 2000;
		double tolerance = radiusM * 0.005;

		DistanceCalculator.BoundingBox box = calculator.boundingBox(lat, lng, radiusM);

		assertThat(calculator.distanceMeters(lat, lng, box.maxLat(), lng)).isGreaterThanOrEqualTo(radiusM - tolerance);
		assertThat(calculator.distanceMeters(lat, lng, box.minLat(), lng)).isGreaterThanOrEqualTo(radiusM - tolerance);
		assertThat(calculator.distanceMeters(lat, lng, lat, box.maxLng())).isGreaterThanOrEqualTo(radiusM - tolerance);
		assertThat(calculator.distanceMeters(lat, lng, lat, box.minLng())).isGreaterThanOrEqualTo(radiusM - tolerance);
	}

	@Test
	void boundingBox_고위도일수록_경도_델타가_cos보정으로_커진다() {
		DistanceCalculator.BoundingBox lowLatBox = calculator.boundingBox(33.0, 127.0, 1000);
		DistanceCalculator.BoundingBox highLatBox = calculator.boundingBox(38.0, 127.0, 1000);

		double lowLatLngDelta = lowLatBox.maxLng() - 127.0;
		double highLatLngDelta = highLatBox.maxLng() - 127.0;

		assertThat(highLatLngDelta).isGreaterThan(lowLatLngDelta);
	}

	@Test
	void boundingBox_허용되지_않은_반경이면_예외() {
		assertThatThrownBy(() -> calculator.boundingBox(37.5, 127.0, 1500))
				.isInstanceOf(InvalidRadiusException.class);
	}

	@Test
	void boundingBox_대한민국_범위_밖_좌표면_예외() {
		assertThatThrownBy(() -> calculator.boundingBox(10.0, 127.0, 1000))
				.isInstanceOf(InvalidCoordinateException.class);
	}
}
