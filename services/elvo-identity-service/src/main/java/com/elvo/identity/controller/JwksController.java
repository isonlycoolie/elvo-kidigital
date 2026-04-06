package com.elvo.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.identity.util.TokenService;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final TokenService tokenService;

    public JwksController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<TokenService.JwksDocument> jwks() {
        return ResponseEntity.ok(tokenService.getJwksDocument());
    }
}
