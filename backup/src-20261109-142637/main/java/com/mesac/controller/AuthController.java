package com.mesac.controller;

import com.mesac.dto.Requests.LoginRequest;
import com.mesac.dto.Requests.PasswordChangeRequest;
import com.mesac.dto.Views.Me;
import com.mesac.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Log in, log out, who am I, change my password. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Me login(@RequestBody LoginRequest req, HttpServletRequest request) {
        return auth.login(req.username(), req.password(), request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        auth.logout(request);
        return Map.of("loggedOut", true);
    }

    /** {"me": null} means nobody is signed in (User view). */
    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Me me = auth.me(request);
        return me == null ? Map.of("signedIn", false) : Map.of("signedIn", true, "me", me);
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody PasswordChangeRequest req, HttpServletRequest request) {
        auth.changeOwnPassword(req.current(), req.next(), request);
        return Map.of("changed", true);
    }
}
