package com.elvo.wallet.service.impl;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.DepositFlowService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultDepositFlowService implements DepositFlowService {

    @Override
    public WalletFlowResult process(DepositCommand command) {
        return WalletFlowResult.failure("Deposit flow not initialized", command.walletId(), "wallet.deposit.failed");
    }
}
