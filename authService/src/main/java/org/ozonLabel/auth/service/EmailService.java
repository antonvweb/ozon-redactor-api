package org.ozonLabel.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    public void sendVerificationCode(String toEmail, String code, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Код подтверждения регистрации");
            message.setText(String.format(
                "Здравствуйте, %s!\n\n" +
                "Ваш код подтверждения: %s\n\n" +
                "Код действителен в течение 10 минут.\n\n" +
                "Если вы не регистрировались, просто проигнорируйте это письмо.",
                name, code
            ));
            
            mailSender.send(message);
            log.info("Verification code sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", toEmail, e);
            throw new RuntimeException("Не удалось отправить email", e);
        }
    }
}
