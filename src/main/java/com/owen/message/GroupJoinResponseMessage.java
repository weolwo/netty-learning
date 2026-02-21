package com.owen.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class GroupJoinResponseMessage extends AbstractResponseMessage {

    public GroupJoinResponseMessage(boolean success, String msg) {
        super(success, msg);
    }

    @Override
    public int getMessageType() {
        return GroupJoinResponseMessage;
    }
}
