package com.ai4cc.conversation.domain.port.in;

import com.ai4cc.conversation.domain.model.valueobject.WarmUpResult;

public interface WarmUpUseCase {

    WarmUpResult warmUp();
}
