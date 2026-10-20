package com.petshop.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetMailer {
    private final ObjectProvider<JavaMailSender> senders;
    @Value("${spring.mail.host:}") private String host;
    @Value("${petshop.mail-from:no-reply@petshop.vn}") private String from;
    @Value("${petshop.public-base-url:http://localhost:8080}") private String baseUrl;

    public void requireConfigured() {
        if (host == null || host.isBlank() || senders.getIfAvailable() == null)
            throw new IllegalStateException("Gửi email chưa được cấu hình. Vui lòng liên hệ quản trị viên để đặt lại mật khẩu.");
    }

    public void send(String email, String token) {
        requireConfigured();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl).path("/reset-password").queryParam("token",token).build().encode().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);message.setTo(email);message.setSubject("PetShop - Đặt lại mật khẩu");
        message.setText("Mở liên kết sau để đặt lại mật khẩu. Liên kết có hiệu lực 30 phút và chỉ dùng một lần:\n" + url);
        senders.getObject().send(message);
    }
}
