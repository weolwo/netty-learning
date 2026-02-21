package com.owen.protocol;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * LengthFieldBasedFrameDecoder 的作用： 它就是水管上的自动切割机。它根据你协议里定义的“长度字段”，精准地咔嚓剪一刀，分出一个完整的包丢给后面的 ObjectInputStream。
 * 它的 4 个核心黑话参数（王者必须掌握）
 * 这个类的构造函数参数多得让人想撞墙，但只要掌握了这四个，你就能随意定义任何“自研协议”。
 * 假设你的协议头是：`魔数(2字节) + 类型(1字节) + 长度(4字节) + 内容(N字节)`
 * | 参数名                  | 含义                       | 你的配置                                  |
 * | ----------------------- | -------------------------- | ----------------------------------------- |
 * | **lengthFieldOffset**   | 长度字段从哪开始？          | **3** (跳过魔数2+类型1)                   |
 * | **lengthFieldLength**   | 长度字段本身占几个字节？     | **4** (int类型占4字节)                    |
 * | **lengthAdjustment**    | 长度字段后面还有“公摊”吗？   | **0** (如果长度不含包头，就填0)           |
 * | **initialBytesToStrip** | 解码后要“掐头”吗？         | **7** (如果你想把 2+1+4 的头切掉只留内容) |
 *
 */

public class ProtocolFrameDecoder extends LengthFieldBasedFrameDecoder {

    public ProtocolFrameDecoder() {
        this(1024, 12, 4, 0, 0);
    }

    public ProtocolFrameDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }
}
