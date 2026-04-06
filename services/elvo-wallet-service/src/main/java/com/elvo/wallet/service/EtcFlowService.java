package com.elvo.wallet.service;

import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

public interface EtcFlowService {

    WalletFlowResult generate(EtcCommand command);

    WalletFlowResult redeem(String code, String idempotencyKey, String deviceId, String sourceIp);
}
