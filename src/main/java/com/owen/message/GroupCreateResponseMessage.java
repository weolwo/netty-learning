package com.owen.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class GroupCreateResponseMessage extends AbstractResponseMessage {

    public GroupCreateResponseMessage(boolean success, String msg) {
        super(success, msg);
    }

    @Override
    public int getMessageType() {
        return GroupCreateResponseMessage;
    }
}
