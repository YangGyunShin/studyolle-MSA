package com.studyolle.admin.common;

/**
 * 내부 서비스 간 통신 관련 상수 모음.
 *
 * [왜 상수 클래스로 분리하는가]
 * "admin-service" 라는 문자열이 코드 여기저기에 흩어져 있으면, 나중에 이름이 바뀔 때
 * 찾기-바꾸기가 번거롭고 오타가 나기 쉽다. 한 곳에 모아두면 IDE 가 사용처를 전부 추적해 주고,
 * 리팩토링 도구도 안전하게 동작한다.
 *
 * [final + private 생성자]
 * 이 클래스는 인스턴스를 만들 이유가 없다. final 로 상속을 막고, private 생성자로 인스턴스화도 막는다.
 * "이건 유틸리티 클래스야, 그냥 상수 가져다 써" 라는 의도를 코드로 표현하는 관용적 패턴이다.
 */
public final class InternalHeaderConstants {

    // 내부 서비스 요청을 식별하는 헤더 이름.
    // 각 서비스의 InternalRequestFilter 가 이 헤더 값을 확인한다.
    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";

    // admin-service 가 다른 서비스의 /internal/** 을 호출할 때 자신의 이름으로 사용한다.
    // account-service 의 InternalRequestFilter ALLOWED 목록에 "admin-service" 가 이미 들어 있다.
    public static final String SERVICE_NAME = "admin-service";

    private InternalHeaderConstants() {
        // 인스턴스화 방지
    }
}