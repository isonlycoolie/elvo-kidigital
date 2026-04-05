package com.elvo.wallet.service.impl;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.EtcFlowService;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultEtcFlowService implements EtcFlowService {

    @Override
    public WalletFlowResult generate(EtcCommand command) {
        return WalletFlowResult.failure("ETC flow not initialized", command.walletId(), "wallet.etc.failed");
    }

    @Override
    public WalletFlowResult redeem(String code, String idempotencyKey) {
        return WalletFlowResult.failure("ETC redeem flow not initialized", null, "wallet.etc.redeem.failed");
    }
}
