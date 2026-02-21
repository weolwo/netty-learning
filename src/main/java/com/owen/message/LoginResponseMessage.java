package com.owen.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public class LoginResponseMessage extends AbstractResponseMessage {

    public LoginResponseMessage(boolean success, String msg) {
        super(success, msg);
    }

    @Override
    public int getMessageType() {
        return LoginResponseMessage;
    }
}
