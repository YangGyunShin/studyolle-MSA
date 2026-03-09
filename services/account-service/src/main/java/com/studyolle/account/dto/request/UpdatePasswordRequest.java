package com.studyolle.account.dto.request;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UpdatePasswordRequest {

    @Length(min = 8, max = 50)
    private String newPassword;

    @Length(min = 8, max = 50)
    private String newPasswordConfirm;
}
