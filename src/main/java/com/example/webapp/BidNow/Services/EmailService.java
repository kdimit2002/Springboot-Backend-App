package com.example.webapp.BidNow.Services;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

/**
 * Email Service
 *
 * Sends emails asynchronously because email delivery is a non-critical side task.
 * Most of the time we do not care exactly when the email will arrive.
 * This keeps the main request thread fast and avoids delaying the user's request processing.
 */
@EnableAsync
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Sends a basic text email asynchronously (so the request thread is not blocked).
    @Async("emailExecutor") // uses the configured executor bean named "emailExecutor"
    public void sendSimpleEmailAsync(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom("bidnowapp@gmail.com");// todo: after claiming domain name change this
        mailSender.send(message);
    }
}
