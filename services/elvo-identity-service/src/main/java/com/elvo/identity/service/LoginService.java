package com.elvo.identity.service;

import com.elvo.identity.dto.request.LoginRequest;
import com.elvo.identity.dto.response.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest request);
}
