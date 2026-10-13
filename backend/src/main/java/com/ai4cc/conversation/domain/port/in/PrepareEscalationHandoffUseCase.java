package com.ai4cc.conversation.domain.port.in;

import com.ai4cc.conversation.domain.model.valueobject.EscalationHandoffCommand;
import com.ai4cc.conversation.domain.model.valueobject.EscalationHandoffReference;

// Stores the audited hand-off and returns only the by-reference token (handoff_id + minimal routing
// metadata) that the channel is allowed to carry (TASK-BE-036 / DEC-013).
public interface PrepareEscalationHandoffUseCase {

    EscalationHandoffReference prepare(EscalationHandoffCommand command);
}
