package com.pharmaprice.common.exception;

/** 좌표가 대한민국 범위(위도 33~39, 경도 124~132)를 벗어났을 때 (docs/API.md §1.3 INVALID_COORDINATE). */
public class InvalidCoordinateException extends RuntimeException {

	public InvalidCoordinateException(double lat, double lng) {
		super("대한민국 범위를 벗어난 좌표입니다: lat=" + lat + ", lng=" + lng);
	}
}
