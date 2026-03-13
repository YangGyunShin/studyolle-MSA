package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역 추가/제거 요청 DTO
 *
 * [모노리틱 참조: ZoneForm.java]
 * "Seoul(서울)/서울특별시" 형태의 문자열을 파싱해서 city, province 를 추출.
 *
 * [모노리틱 ZoneForm 파싱 로직 유지]
 * city: "(" 앞 부분
 * province: "/" 뒤 부분
 */
@Getter
@NoArgsConstructor
public class ZoneRequest {

    @NotBlank
    private String zoneName; // "Seoul(서울)/서울특별시" 형태

    /** zoneName 에서 city 부분 추출 — 모노리틱 ZoneForm.getCityName() 동일 로직 */
    public String getCityName() {
        return zoneName.substring(0, zoneName.indexOf("("));
    }

    /** zoneName 에서 province 부분 추출 — 모노리틱 ZoneForm.getProvinceName() 동일 로직 */
    public String getProvinceName() {
        return zoneName.substring(zoneName.indexOf("/") + 1);
    }
}