package com.studyolle.account.infra.mail;

public interface EmailService {

    void sendEmail(EmailMessage emailMessage);
}