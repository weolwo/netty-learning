package com.owen.message;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = true)
public abstract class AbstractResponseMessage extends Message {
    private boolean success;
    private String msg;

    public AbstractResponseMessage() {
    }

    public AbstractResponseMessage(boolean success, String msg) {
        this.success = success;
        this.msg = msg;
    }
}
