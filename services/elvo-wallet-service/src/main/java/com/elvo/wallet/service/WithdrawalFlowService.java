package com.elvo.wallet.service;

import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;

public interface WithdrawalFlowService {

    WalletFlowResult process(WithdrawalCommand command);
}
