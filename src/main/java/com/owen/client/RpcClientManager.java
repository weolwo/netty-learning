package com.owen.client;

import com.owen.client.handler.RpcResponseMessageHandler;
import com.owen.config.ApplicationConfig;
import com.owen.message.RpcRequestMessage;
import com.owen.protocol.MessageCodecSharable;
import com.owen.protocol.ProtocolFrameDecoder;
import com.owen.protocol.SequenceIdGenerator;
import com.owen.server.service.HelloService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcClientManager {
    private static Channel channel = null;
    private static final Object LOCK = new Object();

    private static final int MAX_RETRY = 3; // 最大重连次数

    private static LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
    private static MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
    private static RpcResponseMessageHandler RPC_HANDLER = new RpcResponseMessageHandler();
    private static NioEventLoopGroup group = new NioEventLoopGroup();
    private static Bootstrap bootstrap = new Bootstrap();
    // 关键：初始容量为 1 的倒计数锁
    private static final CountDownLatch connectLatch = new CountDownLatch(1);

    static {
        // 静态块初始化，只执行一次
        bootstrap.channel(NioSocketChannel.class)
                .group(group)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ProtocolFrameDecoder());
                        ch.pipeline().addLast(LOGGING_HANDLER);
                        ch.pipeline().addLast(MESSAGE_CODEC);
                        ch.pipeline().addLast(RPC_HANDLER);
                    }
                });
    }

    private static void connect(String ip, int port, int retry) {
        // 异步尝试连接
        ChannelFuture channelFuture = bootstrap.connect(ip, port);
        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                channel = future.channel();
                System.out.println("连接成功!");
                connectLatch.countDown();
            } else {
                if (retry <= 0) {
                    System.err.println("重连次数用完，放弃治疗...");
                    // 只有真的不连了，才考虑是否关掉 group (通常客户端长连接不建议关)
                    future.channel().close();
                    group.shutdownGracefully();
                    connectLatch.countDown();
                    return;
                }
                int order = (MAX_RETRY - retry) + 1;
                // 指数退避：等待时间随次数增加，防止把服务端冲垮
                int delay = 1 << order;
                System.err.println("连接失败，第" + order + "次重连，等待" + delay + "秒...");

                // 变量捕获修复
                final int nextRetry = retry - 1;
                // 此时 group 是开着的，任务一定能执行
                future.channel().eventLoop().schedule(() -> connect(ip, port, nextRetry), delay, TimeUnit.SECONDS);
            }
        });
    }

    // 获取唯一的 channel 对象
    public static Channel getChannel() {
        if (channel != null) {
            return channel;
        }
        synchronized (LOCK) { //  t2
            if (channel != null) { // t1
                return channel;
            }
            connect("localhost", ApplicationConfig.getServerPort(), MAX_RETRY);
            try {
                // 如果连接还没好，这里会阻塞住业务线程，直到 latch 变成 0
                // 建议增加超时时间，比如等 10 秒还不来就抛异常
                connectLatch.await();
                if (channel == null || !channel.isActive()) {
                    throw new RuntimeException("连接获取失败，链路未就绪");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return channel;
        }
    }

    public static void main(String[] args) {
        HelloService proxyService = getProxyService(HelloService.class);
        if (proxyService != null) {
            String result = proxyService.sayHello("山东");
            System.out.println(result);
        }
    }

    // 创建代理类
    public static <T> T getProxyService(Class<T> serviceClass) {
        ClassLoader loader = serviceClass.getClassLoader();
        Class<?>[] interfaces = new Class[]{serviceClass};
        //                                                            sayHello  "张三"
        Object o = Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {
            // 1. 将方法调用转换为 消息对象
            int sequenceId = SequenceIdGenerator.nextId();
            RpcRequestMessage msg = new RpcRequestMessage(
                    sequenceId,
                    serviceClass.getName(),
                    method.getName(),
                    method.getReturnType(),
                    method.getParameterTypes(),
                    args
            );
            getChannel().writeAndFlush(msg);
            if (channel == null) {
                return null;
            }
            // 2. 将消息对象发送出去

            // 3. 准备一个空 Promise 对象，来接收结果             指定 promise 对象异步接收结果线程
            DefaultPromise<Object> promise = new DefaultPromise<>(channel.eventLoop());
            RpcResponseMessageHandler.PROMISES.put(sequenceId, promise);

//            promise.addListener(future -> {
//                // 线程
//            });

            // 4. 等待 promise 结果
            promise.await();
            if (promise.isSuccess()) {
                // 调用正常
                return promise.getNow();
            } else {
                // 调用失败
                throw new RuntimeException(promise.cause());
            }
        });
        return (T) o;
    }
}
