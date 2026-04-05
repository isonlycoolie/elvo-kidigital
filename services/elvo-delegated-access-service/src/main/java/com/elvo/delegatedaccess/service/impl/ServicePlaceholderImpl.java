package com.elvo.delegatedaccess.service.impl;

import com.elvo.delegatedaccess.service.ServicePlaceholder;
import org.springframework.stereotype.Service;

@Service
public class ServicePlaceholderImpl implements ServicePlaceholder {
    @Override
    public String getStatus() {
        return "INITIALIZED";
    }
}
