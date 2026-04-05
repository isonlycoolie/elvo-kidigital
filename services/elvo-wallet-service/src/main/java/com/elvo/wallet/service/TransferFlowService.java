package com.elvo.wallet.service;

import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

public interface TransferFlowService {

    WalletFlowResult process(TransferCommand command);
}
