package com.elvo.agent.service.impl;

import com.elvo.agent.service.ServicePlaceholder;
import org.springframework.stereotype.Service;

@Service
public class ServicePlaceholderImpl implements ServicePlaceholder {
    @Override
    public String getStatus() {
        return "INITIALIZED";
    }
}
