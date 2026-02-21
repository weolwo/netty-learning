package com.owen.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class GroupQuitResponseMessage extends AbstractResponseMessage {
    public GroupQuitResponseMessage(boolean success, String msg) {
        super(success, msg);
    }

    @Override
    public int getMessageType() {
        return GroupQuitResponseMessage;
    }
}
