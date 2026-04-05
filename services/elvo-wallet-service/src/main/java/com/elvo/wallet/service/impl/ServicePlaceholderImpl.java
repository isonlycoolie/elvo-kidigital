package com.elvo.wallet.service.impl;

import com.elvo.wallet.service.ServicePlaceholder;
import org.springframework.stereotype.Service;

@Service
public class ServicePlaceholderImpl implements ServicePlaceholder {
    @Override
    public String getStatus() {
        return "INITIALIZED";
    }
}
