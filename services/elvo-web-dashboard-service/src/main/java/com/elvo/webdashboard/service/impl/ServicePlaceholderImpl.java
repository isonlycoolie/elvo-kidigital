package com.elvo.webdashboard.service.impl;

import com.elvo.webdashboard.service.ServicePlaceholder;
import org.springframework.stereotype.Service;

@Service
public class ServicePlaceholderImpl implements ServicePlaceholder {
    @Override
    public String getStatus() {
        return "INITIALIZED";
    }
}
