package com.elvo.notification.service.impl;

import com.elvo.notification.service.ServicePlaceholder;
import org.springframework.stereotype.Service;

@Service
public class ServicePlaceholderImpl implements ServicePlaceholder {
    @Override
    public String getStatus() {
        return "INITIALIZED";
    }
}
