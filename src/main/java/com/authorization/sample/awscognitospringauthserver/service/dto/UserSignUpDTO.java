package com.authorization.sample.awscognitospringauthserver.service.dto;

import com.authorization.sample.awscognitospringauthserver.annotation.ValidPassword;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
public class UserSignUpDTO {

    @NotBlank
    @NotNull
    @Email
    private String email;

    @ValidPassword
    private String password;

    @NotBlank
    @NotNull
    private String name;

    private String tenantId;
    private String tenatName;
    private String firsttime_log_remain;
    private String is_self_service_usr;
    private String is_deleted;
    private String last_time_pd_updated;
    private String nonexpired;
    private String nonexpired_credls;
    private String nonlocked;
    private String office_id;
    private String pd_never_expires;
    private String staff_id;
    private String lastname;


    private String phoneNumber;

    @NotNull
    @NotEmpty
    private Set<String> roles;

}
