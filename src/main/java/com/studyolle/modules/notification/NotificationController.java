package com.studyolle.modules.notification;

import com.studyolle.modules.account.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * 알림(Notification) 웹 컨트롤러
 *
 * - 알림 목록 조회 (읽지 않은 알림 / 읽은 알림)
 * - 알림 읽음 처리 (markAsRead)
 * - 읽은 알림 일괄 삭제
 *
 * =====================================================
 * [리팩토링: Repository 직접 접근 제거]
 *
 * 기존에는 이 Controller가 NotificationRepository를 직접 주입받아
 * findBy..., countBy..., deleteBy... 등을 직접 호출했음.
 *
 * 문제점:
 *   - 계층 구조(Controller -> Service -> Repository) 위반
 *   - 비즈니스 로직(알림 분류, 읽음 처리)이 Controller에 흩어짐
 *   - 트랜잭션 경계가 모호해짐 (각 repository 호출이 별도 트랜잭션)
 *
 * 변경 후:
 *   - Controller는 NotificationService만 의존
 *   - 모든 데이터 접근과 비즈니스 로직은 Service 계층에서 처리
 *   - Controller는 HTTP 요청/응답 처리와 Model 바인딩만 담당
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 읽지 않은 새 알림 조회
     *
     * - 최초로 알림 페이지를 열 때 호출됨
     * - 알림 목록만 표시하고, 읽음 처리는 하지 않음
     *   → 개별 알림 클릭 시 /notifications/{id}/read 엔드포인트에서 읽음 처리
     *   → "모두 읽음 처리" 버튼으로 일괄 처리도 가능
     */
    @GetMapping("/notifications")
    public String getNotifications(Account account, Model model) {
        List<Notification> notifications = notificationService.getNewNotifications(account);
        long numberOfChecked = notificationService.countByChecked(account, true);

        putCategorizedNotifications(model, notifications, numberOfChecked, notifications.size());
        model.addAttribute("isNew", true);

        return "notification/list";
    }

    @GetMapping("/notifications/{id}/read")
    public String readNotification(Account account, @PathVariable Long id) {
        Notification notification = notificationService.markAsReadAndGet(account, id);
        return "redirect:" + notification.getLink();
    }

    /**
     * 새 알림 모두 읽음 처리 (명시적 사용자 액션)
     */
    @PostMapping("/notifications/mark-all-read")
    public String markAllAsRead(Account account) {
        List<Notification> newNotifications = notificationService.getNewNotifications(account);
        notificationService.markAsRead(newNotifications);
        return "redirect:/notifications";
    }

    /**
     * 읽은 과거 알림 목록 조회
     *
     * - 알림 목록 하단에서 '과거 알림 보기'를 선택하면 호출됨
     */
    @GetMapping("/notifications/old")
    public String getOldNotifications(Account account, Model model) {
        List<Notification> notifications = notificationService.getOldNotifications(account);
        long numberOfNotChecked = notificationService.countByChecked(account, false);

        putCategorizedNotifications(model, notifications, notifications.size(), numberOfNotChecked);
        model.addAttribute("isNew", false);

        return "notification/list";
    }

    /**
     * 읽은 알림 전체 삭제 처리
     *
     * - 웹에서 '읽은 알림 전체삭제' 버튼 누르면 호출됨
     * - 삭제 후 새 알림 목록 페이지로 리다이렉트
     */
    @DeleteMapping("/notifications")
    public String deleteNotifications(Account account) {
        notificationService.deleteOldNotifications(account);
        return "redirect:/notifications";
    }

    /**
     * 유형별로 분류된 알림 데이터를 Model에 담아 View에 전달
     *
     * - NotificationService.categorize()에서 분류된 결과를 각각의 Model Attribute로 매핑
     * - notification/list.html 에서 탭별로 나눠 표시할 때 사용
     *
     * =====================================================
     * [왜 Model 바인딩은 Controller에 남겨두는가?]
     *
     * "어떤 이름으로 model에 넣을지"는 View(HTML) 템플릿과의 계약이므로
     * 표현 계층인 Controller의 책임.
     * Service는 순수한 데이터 분류만 담당하고, View에 종속되지 않음.
     */
    private void putCategorizedNotifications(Model model,
                                             List<Notification> notifications,
                                             long numberOfChecked,
                                             long numberOfNotChecked) {
        // Service에서 유형별로 분류된 리스트를 받아옴
        List<List<Notification>> categorized = notificationService.categorize(notifications);

        model.addAttribute("numberOfNotChecked", numberOfNotChecked);
        model.addAttribute("numberOfChecked", numberOfChecked);
        model.addAttribute("notifications", notifications);
        model.addAttribute("newStudyNotifications", categorized.get(0));
        model.addAttribute("eventEnrollmentNotifications", categorized.get(1));
        model.addAttribute("watchingStudyNotifications", categorized.get(2));
        model.addAttribute("newBoardNotifications", categorized.get(3));
    }
}