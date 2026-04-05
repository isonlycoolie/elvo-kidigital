package com.elvo.wallet.service;

import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

public interface DepositFlowService {

    WalletFlowResult process(DepositCommand command);
}
