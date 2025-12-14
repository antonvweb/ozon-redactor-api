package org.ozonLabel.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.auth.EmailSendingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name:OzonLabel}")
    private String appName;

    /**
     * Send verification code with retry logic
     */
    @Retryable(
            value = {MailException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendVerificationCode(String toEmail, String code, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(String.format("Код подтверждения регистрации — %s", appName));
            message.setText(buildVerificationEmail(name, code));

            mailSender.send(message);
            log.info("Verification code sent successfully");
        } catch (MailException e) {
            log.error("Failed to send verification email after retries", e);
            throw new EmailSendingException("Не удалось отправить письмо с подтверждением.");
        } catch (Exception e) {
            log.error("Unexpected error sending email", e);
            throw new EmailSendingException("Не удалось отправить электронное письмо");
        }
    }

    /**
     * Send password reset email (for future implementation)
     */
    @Retryable(
            value = {MailException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void sendPasswordResetEmail(String toEmail, String resetLink, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(String.format("Сброс пароля — %s", appName));
            message.setText(buildPasswordResetEmail(name, resetLink));

            mailSender.send(message);
            log.info("Password reset email sent successfully");
        } catch (MailException e) {
            log.error("Failed to send password reset email after retries", e);
            throw new EmailSendingException("Не удалось отправить письмо для сброса пароля.");
        }
    }

    /**
     * Send welcome email after registration
     */
    public void sendWelcomeEmail(String toEmail, String name) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(String.format("Добро пожаловать в %s!", appName));
            message.setText(buildWelcomeEmail(name));

            mailSender.send(message);
            log.info("Welcome email sent successfully");
        } catch (Exception e) {
            // Don't throw exception for welcome email - it's not critical
            log.warn("Failed to send welcome email", e);
        }
    }

    // Email template builders

    private String buildVerificationEmail(String name, String code) {
        return String.format(
                "Привет, %s!\n" +
                        "\n" +
                        "Ваш код подтверждения: %s\n" +
                        "\n" +
                        "Этот код действителен в течение 10 минут.\n" +
                        "\n" +
                        "Если вы не запрашивали этот код, просто проигнорируйте это письмо.\n" +
                        "\n" +
                        "С уважением,\n" +
                        "Команда %s\n",
                name, code, appName
        );
    }

    private String buildPasswordResetEmail(String name, String resetLink) {
        return String.format(
                "Привет, %s!\n" +
                        "\n" +
                        "Вы запросили сброс пароля. Перейдите по ссылке ниже, чтобы продолжить:\n" +
                        "\n" +
                        "%s\n" +
                        "\n" +
                        "Эта ссылка действительна в течение 1 часа.\n" +
                        "\n" +
                        "Если вы не запрашивали это действие, просто проигнорируйте это письмо — ваш пароль останется без изменений.\n" +
                        "\n" +
                        "С уважением,\n" +
                        "Команда %s\n",
                name, resetLink, appName
        );
    }

    private String buildWelcomeEmail(String name) {
        return String.format(
                "Привет, %s!\n" +
                        "\n" +
                        "Добро пожаловать в %s! Ваш аккаунт был успешно создан.\n" +
                        "\n" +
                        "Теперь вы можете пользоваться всеми возможностями нашей платформы.\n" +
                        "\n" +
                        "Если у вас возникнут вопросы, вы всегда можете обратиться в нашу службу поддержки.\n" +
                        "\n" +
                        "С уважением,\n" +
                        "Команда %s\n",
                name, appName, appName
        );
    }
}