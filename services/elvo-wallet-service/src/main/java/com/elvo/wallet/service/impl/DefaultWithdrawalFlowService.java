package com.elvo.wallet.service.impl;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.WithdrawalFlowService;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;

@Service
public class DefaultWithdrawalFlowService implements WithdrawalFlowService {

    @Override
    public WalletFlowResult process(WithdrawalCommand command) {
        return WalletFlowResult.failure("Withdrawal flow not initialized", command.walletId(), "wallet.withdrawal.failed");
    }
}
