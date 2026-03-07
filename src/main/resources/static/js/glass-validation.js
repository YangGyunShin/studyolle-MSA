/**
 * glass-validation.js
 * 글래스모피즘 로그인/회원가입 페이지 공용 유효성 검사 스크립트
 *
 * [기능]
 *   1. 실시간 입력 유효성 검사 + 체크 아이콘 표시
 *   2. Bootstrap 네이티브 폼 유효성 검사
 *
 * [동작 방식]
 *   - 각 input의 data-validate 속성값에 따라 유효성 규칙 적용
 *   - 통과하면 .input-wrapper에 .is-valid 클래스 추가
 *   - CSS에서 .is-valid .check-icon에 바운스 애니메이션 적용
 *
 * [유효성 규칙]
 *   - nickname : 3~20자, 공백 없이 영문/숫자/한글만 허용
 *   - email    : 기본 이메일 형식 정규식 검사
 *   - password : 8자 이상
 *   - username : 1자 이상 (이메일 또는 닉네임이므로 느슨한 검증)
 */
document.addEventListener('DOMContentLoaded', function() {

    var validators = {
        nickname: function(v) {
            return v.length >= 3 && v.length <= 20 && /^[a-zA-Z0-9가-힣]+$/.test(v);
        },
        email: function(v) {
            return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
        },
        password: function(v) {
            return v.length >= 8;
        },
        username: function(v) {
            return v.trim().length >= 1;
        }
    };

    document.querySelectorAll('input[data-validate]').forEach(function(input) {
        var type = input.getAttribute('data-validate');
        var wrapper = input.closest('.input-wrapper');

        input.addEventListener('input', function() {
            if (validators[type] && validators[type](input.value)) {
                wrapper.classList.add('is-valid');
            } else {
                wrapper.classList.remove('is-valid');
            }
        });

        // 페이지 로드 시 이미 값이 있는 경우 (Thymeleaf 서버사이드 렌더링 대응)
        if (input.value && validators[type] && validators[type](input.value)) {
            wrapper.classList.add('is-valid');
        }
    });

    // Bootstrap 폼 유효성 검사
    document.querySelectorAll('.needs-validation').forEach(function(form) {
        form.addEventListener('submit', function(event) {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
            }
            form.classList.add('was-validated');
        }, false);
    });
});
