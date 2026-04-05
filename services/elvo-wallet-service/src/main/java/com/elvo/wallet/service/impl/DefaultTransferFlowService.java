package com.elvo.wallet.service.impl;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.TransferFlowService;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultTransferFlowService implements TransferFlowService {

    @Override
    public WalletFlowResult process(TransferCommand command) {
        return WalletFlowResult.failure("Transfer flow not initialized", command.sourceWalletId(), "wallet.transfer.failed");
    }
}
