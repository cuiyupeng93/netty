/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Abstract base class for {@link EventLoopGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 *
 * 实现 EventLoopGroup，继承 MultithreadEventExecutorGroup 的抽象类。
 * 以多线程的多线程方式处理它们的任务
 */
public abstract class MultithreadEventLoopGroup extends MultithreadEventExecutorGroup implements EventLoopGroup {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MultithreadEventLoopGroup.class);

    // EventLoopGroup的默认线程数
    private static final int DEFAULT_EVENT_LOOP_THREADS;

    static {
        // 优先取 io.netty.eventLoopThreads 配置的线程数，否则使用 CPU 核数 * 2
        DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        if (logger.isDebugEnabled()) {
            logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
        }
    }

    /**
     * 构造方法
     * 如果没有传入线程数，使用 DEFAULT_EVENT_LOOP_THREADS
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
        // 当没有传入nThreads或者传入0时，会取当前CPU核数*2作为默认值
        // 下一步调用父类MultithreadEventExecutorGroup的构造方法
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
    }

    /**
     * 构造方法
     * 如果没有传入线程数，使用 DEFAULT_EVENT_LOOP_THREADS
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, ThreadFactory, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, threadFactory, args);
    }

    /**
     * 构造方法
     * 如果没有传入线程数，使用 DEFAULT_EVENT_LOOP_THREADS
     * @see MultithreadEventExecutorGroup#MultithreadEventExecutorGroup(int, Executor,
     * EventExecutorChooserFactory, Object...)
     */
    protected MultithreadEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                                     Object... args) {
        super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, chooserFactory, args);
    }

    /**
     * 创建线程工厂对象
     * 覆盖父类方法，增加了线程优先级为 Thread.MAX_PRIORITY
     * @return
     */
    @Override
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass(), Thread.MAX_PRIORITY);
    }

    /**
     * 选择下一个 EventLoop 对象
     * 覆盖父类方法，将返回值转换成 EventLoop 类
     * @return
     */
    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    /**
     * 创建 EventExecutor 对象
     * 覆盖父类方法，返回值改为 EventLoop 类
     * @param executor
     * @param args
     * @return
     * @throws Exception
     */
    @Override
    protected abstract EventLoop newChild(Executor executor, Object... args) throws Exception;

    @Override
    public ChannelFuture register(Channel channel) {
        // next()方法是调用的EventExecutorChooser的next方法，从EventLoopGroup的children数组中获取一个EventLoop
        // 再把传入的channel注册到这个eventLoop上
        // 下一步调用的是 SingleThreadEventLoop.register方法
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }

    @Deprecated
    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return next().register(channel, promise);
    }

}
