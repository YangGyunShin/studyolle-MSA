package com.studyolle.modules.event.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.dto.EventForm;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.repository.EventRepository;
import com.studyolle.modules.event.service.EventService;
import com.studyolle.modules.event.validator.EventValidator;
import com.studyolle.modules.study.entity.Study;
import com.studyolle.modules.study.repository.StudyRepository;
import com.studyolle.modules.study.service.StudyService;
import com.studyolle.modules.study.service.StudySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * 이벤트(모임) CRUD + 목록 조회 전용 컨트롤러
 * ============================================================
 *
 * [담당 범위]
 * - 이벤트 생성 폼/처리, 상세 조회, 목록 조회, 수정 폼/처리, 삭제
 * - Event 엔티티 자체의 생명주기(CRUD)를 관리하는 모든 웹 요청
 *
 * [분리 기준 - 기능적 응집도 (Functional Cohesion)]
 * - Event 엔티티의 생명주기 관리에만 집중
 * - Enrollment(참가 신청) 관련 기능은 별도 컨트롤러로 분리:
 *   - EnrollmentController: 일반 사용자의 참가 신청/취소
 *   - EnrollmentManageController: 운영자의 승인/거절/출석 관리
 *
 * [컨트롤러 분리 전체 구조]
 * --------------------------------------------------------------------------------
 *  컨트롤러                       | 대상        | 사용자      | 권한 패턴
 * --------------------------------------------------------------------------------
 *  EventController (현재)        | Event      | 운영자+전체 | getStudyToUpdate/Status
 *  EnrollmentController         | Enrollment | 일반 참가자 | getStudyToEnroll
 *  EnrollmentManageController   | Enrollment | 운영자     | getStudyToUpdate
 * --------------------------------------------------------------------------------
 *
 * [URL 패턴]
 * - 모든 URL은 /study/{path} 하위에 매핑
 * - 이벤트 CRUD: /new-event, /events, /events/{id}, /events/{id}/edit
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/study/{path}")
public class EventController {

    private final StudyService studyService;
    private final StudySettingsService studySettingsService;
    private final EventService eventService;
    private final ModelMapper modelMapper;
    private final EventValidator eventValidator;
    private final EventRepository eventRepository;
    private final StudyRepository studyRepository;

    /**
     * EventForm 바인딩 시 커스텀 유효성 검사기를 자동 적용
     *
     * - @InitBinder는 이 컨트롤러 범위에서만 동작
     * - 'eventForm' 이름으로 바인딩되는 객체에만 적용
     * - @Valid 사용 시 JSR-303 검증 + EventValidator 커스텀 검증이 동시에 실행됨
     */
    @InitBinder("eventForm")
    public void initBinder(WebDataBinder webDataBinder) {
        webDataBinder.addValidators(eventValidator);
    }

    // ============================
    // 이벤트 생성
    // ============================

    /**
     * 모임(Event) 생성 폼 화면 제공
     *
     * - GET /study/{path}/new-event
     * - 스터디 운영자(리더/관리자)만 접근 가능
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * HTTP GET /study/{path}/new-event
     *  -> StudyService.getStudyToUpdateStatus() (권한 + 존재 확인)
     *  -> Model에 Study, Account, EventForm(빈 객체) 전달
     *  -> event/form.html 렌더링
     */
    @GetMapping("/new-event")
    public String newEventForm(Account account,
                               @PathVariable String path, Model model) {

        // 권한 확인 및 Study 조회 (운영자만 이벤트 생성 가능)
        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        model.addAttribute(study);
        model.addAttribute(account);
        model.addAttribute(new EventForm());

        return "event/form";
    }

    /**
     * 모임 생성 폼 제출 처리
     *
     * - POST /study/{path}/new-event
     * - @Valid로 JSR-303 + EventValidator 커스텀 검증 동시 수행
     * - 검증 성공 -> 이벤트 저장 -> 상세 페이지로 리다이렉트
     * - 검증 실패 -> 폼 재렌더링 (입력값 유지)
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * POST /study/{path}/new-event
     *  -> ArgumentResolver: @Valid EventForm 바인딩 + WebDataBinder 검증
     *  -> 검증 실패 -> event/form 반환 (입력값 유지)
     *  -> 검증 성공 -> ModelMapper: EventForm -> Event 변환
     *  -> EventService.createEvent() -> DB 저장
     *  -> 이벤트 상세 URL로 리다이렉트 (PRG 패턴)
     */
    @PostMapping("/new-event")
    public String newEventSubmit(Account account,
                                 @PathVariable String path,
                                 @Valid EventForm eventForm,
                                 Errors errors,
                                 Model model) {

        Study study = studySettingsService.getStudyToUpdateStatus(account, path);

        if (errors.hasErrors()) {
            model.addAttribute(account);
            model.addAttribute(study);
            return "event/form";
        }

        Event event = modelMapper.map(eventForm, Event.class);
        eventService.createEvent(event, study, account);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    // ============================
    // 이벤트 조회
    // ============================

    /**
     * 단일 이벤트 상세 페이지
     *
     * - GET /study/{path}/events/{id}
     * - 모든 사용자가 접근 가능 (별도 권한 검증 없음)
     * - @PathVariable("id") Event event: Spring Data JPA DomainClassConverter가
     *   id 값으로 EventRepository.findById() 자동 호출하여 엔티티 주입
     */
    @GetMapping("/events/{id}")
    public String getEvent(Account account,
                           @PathVariable String path,
                           @PathVariable("id") Event event,
                           Model model) {

        model.addAttribute(account);
        model.addAttribute(event);
        model.addAttribute(studyRepository.findStudyWithManagersByPath(path));

        return "event/view";
    }

    /**
     * 스터디별 이벤트 목록 조회
     *
     * - GET /study/{path}/events
     * - 모든 사용자 접근 가능
     * - 현재 시간 기준으로 진행중/예정(newEvents)과 종료된(oldEvents) 이벤트를 분류
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * GET /study/{path}/events
     *  -> StudyService.getStudy() (스터디 조회)
     *  -> EventRepository.findByStudyOrderByStartDateTime() (전체 이벤트 조회)
     *  -> 현재 시각 기준 newEvents / oldEvents 분류
     *  -> study/events.html 렌더링
     */
    @GetMapping("/events")
    public String viewStudyEvents(Account account,
                                  @PathVariable String path,
                                  Model model) {

        Study study = studyService.getStudy(path);

        model.addAttribute(account);
        model.addAttribute(study);

        List<Event> events = eventRepository.findByStudyOrderByStartDateTime(study);
        List<Event> newEvents = new ArrayList<>();
        List<Event> oldEvents = new ArrayList<>();
        events.forEach(e -> {
            if (e.getEndDateTime().isBefore(LocalDateTime.now())) {
                oldEvents.add(e);
            } else {
                newEvents.add(e);
            }
        });

        model.addAttribute("newEvents", newEvents);
        model.addAttribute("oldEvents", oldEvents);

        return "study/events";
    }

    // ============================
    // 이벤트 수정
    // ============================

    /**
     * 이벤트 수정 폼 화면 제공
     *
     * - GET /study/{path}/events/{id}/edit
     * - 스터디 운영자만 접근 가능
     * - 기존 Event -> EventForm 변환하여 폼 초기값으로 바인딩
     */
    @GetMapping("/events/{id}/edit")
    public String updateEventForm(Account account,
                                  @PathVariable String path,
                                  @PathVariable("id") Event event,
                                  Model model) {

        Study study = studySettingsService.getStudyToUpdate(account, path);

        model.addAttribute(study);
        model.addAttribute(account);
        model.addAttribute(event);
        model.addAttribute(modelMapper.map(event, EventForm.class));

        return "event/update-form";
    }

    /**
     * 이벤트 수정 폼 제출 처리
     *
     * - POST /study/{path}/events/{id}/edit
     * - 이벤트 유형(EventType)은 수정 불가 (기존값 강제 유지)
     * - 커스텀 검증: 기존 참가 신청 인원과 변경된 모집인원 비교
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * POST /events/{id}/edit
     *  -> 권한 확인 (getStudyToUpdate)
     *  -> eventType 기존값 유지 (수정 불가 필드)
     *  -> EventValidator.validateUpdateForm() (커스텀 검증)
     *  -> 검증 실패 -> 폼 재렌더링
     *  -> 검증 성공 -> EventService.updateEvent() -> dirty checking 저장
     *  -> 상세 페이지로 리다이렉트
     */
    @PostMapping("/events/{id}/edit")
    public String updateEventSubmit(Account account,
                                    @PathVariable String path,
                                    @PathVariable("id") Event event,
                                    @Valid EventForm eventForm,
                                    Errors errors,
                                    Model model) {

        Study study = studySettingsService.getStudyToUpdate(account, path);

        eventForm.setEventType(event.getEventType());
        eventValidator.validateUpdateForm(eventForm, event, errors);

        if (errors.hasErrors()) {
            model.addAttribute(account);
            model.addAttribute(study);
            model.addAttribute(event);
            return "event/update-form";
        }

        eventService.updateEvent(event, eventForm);

        return "redirect:/study/" + study.getEncodedPath() + "/events/" + event.getId();
    }

    // ============================
    // 이벤트 삭제
    // ============================

    /**
     * 이벤트 삭제 처리
     *
     * - DELETE /study/{path}/events/{id}
     * - 스터디 운영자(리더/관리자)만 삭제 가능
     * - 삭제 후 이벤트 목록 페이지로 리다이렉트 (PRG 패턴)
     *
     * =======================
     * 전체 데이터 흐름
     * =======================
     * DELETE /study/{path}/events/{id}
     *  -> StudyService.getStudyToUpdateStatus() (권한 검증)
     *  -> EventService.deleteEvent() (삭제 + 알림 이벤트 발행)
     *  -> 이벤트 목록 페이지로 리다이렉트
     */
    @DeleteMapping("/events/{id}")
    public String cancelEvent(Account account,
                              @PathVariable String path,
                              @PathVariable("id") Event event) {

        Study study = studySettingsService.getStudyToUpdateStatus(account, path);
        eventService.deleteEvent(event);

        return "redirect:/study/" + study.getEncodedPath() + "/events";
    }
}