package com.studyolle.account.dto.request;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
// nullpointexception 발생을 막기 위해 NoArgsConstructor 사용
public class UpdateProfileRequest {

    @Length(max = 35)
    private String bio;

    @Length(max = 50)
    private String url;

    @Length(max = 50)
    private String occupation;

    @Length(max = 50)
    private String location;

    private String profileImage;
}
