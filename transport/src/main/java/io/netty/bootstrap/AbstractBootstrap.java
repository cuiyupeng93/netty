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

package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link AbstractBootstrap} is a helper class that makes it easy to bootstrap a {@link Channel}. It support
 * method-chaining to provide an easy way to configure the {@link AbstractBootstrap}.
 *
 * <p>When not used in a {@link ServerBootstrap} context, the {@link #bind()} methods are useful for connectionless
 * transports such as datagram (UDP).</p>
 *
 * 译：AbstractBootstrap 是一个使启动 Channel 变得更容易的帮助类。它可以使用方法链简单的配置一个 AbstractBootstrap。
 * 如果不是用在 ServerBootstrap 上下文中，bind方法对无连接传输（如UDP）有用。
 *
 * 注：
 * 1、AbstractBootstrap 可以理解为"启动器"，它有两个子类 ServerBootstrap（用于启动服务端），Bootstrap（用于启动客户端）。
 * 2、它声明了两个泛型B、C，B继承 AbstractBootstrap 类，用于表示自身的类型；C ：继承 Channel 类，表示表示创建的 Channel 类型。
 */
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {

    volatile EventLoopGroup group;
    /**
     * Channel 工厂，用于创建 Channel 对象。
     */
    @SuppressWarnings("deprecation")
    private volatile ChannelFactory<? extends C> channelFactory;
    /**
     * 本地地址
     */
    private volatile SocketAddress localAddress;
    /**
     * 可选项集合
     */
    private final Map<ChannelOption<?>, Object> options = new ConcurrentHashMap<ChannelOption<?>, Object>();
    /**
     * 属性集合
     */
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();
    /**
     * 处理器
     */
    private volatile ChannelHandler handler;

    AbstractBootstrap() {
        // Disallow extending from a different package.
    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        options.putAll(bootstrap.options);
        attrs.putAll(bootstrap.attrs);
    }

    /**
     * 设置 EventLoopGroup 到 group 中
     */
    public B group(EventLoopGroup group) {
        ObjectUtil.checkNotNull(group, "group");
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        //返回自己，链式调用
        return self();
    }

    /**
     * 返回自己
     * @return
     */
    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * 设置要被实例化的 Channel 的类
     */
    public B channel(Class<? extends C> channelClass) {
        return channelFactory(
                new ReflectiveChannelFactory<C>(ObjectUtil.checkNotNull(channelClass, "channelClass"))
        );
    }

    /**
     * @deprecated Use {@link #channelFactory(io.netty.channel.ChannelFactory)} instead.
     * 可以判断出，最初 ChannelFactory 在 bootstrap 中，后重构到 channel 包中。
     */
    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        ObjectUtil.checkNotNull(channelFactory, "channelFactory");
        if (this.channelFactory != null) {
            //不允许重复设置
            throw new IllegalStateException("channelFactory set already");
        }

        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * {@link io.netty.channel.ChannelFactory} which is used to create {@link Channel} instances from
     * when calling {@link #bind()}. This method is usually only used if {@link #channel(Class)}
     * is not working for you because of some more complex needs. If your {@link Channel} implementation
     * has a no-args constructor, its highly recommend to just use {@link #channel(Class)} to
     * simplify your code.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public B channelFactory(io.netty.channel.ChannelFactory<? extends C> channelFactory) {
        return channelFactory((ChannelFactory<C>) channelFactory);
    }

    /**
     * 设置创建 Channel 的本地地址。有四个重载的方法
     * 一般情况下，不会调用该方法进行配置，而是调用 #bind(...) 方法
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 设置创建 Channel 的可选项
     */
    public <T> B option(ChannelOption<T> option, T value) {
        ObjectUtil.checkNotNull(option, "option");
        if (value == null) { // 空，意味着移除
            options.remove(option);
        } else { // 非空，进行修改
            options.put(option, value);
        }
        return self();
    }

    /**
     * 设置创建 Channel 的属性
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        ObjectUtil.checkNotNull(key, "key");
        if (value == null) { // 空，意味着移除
            attrs.remove(key);
        } else { // 非空，进行修改
            attrs.put(key, value);
        }
        return self();
    }

    /**
     * Validate all the parameters. Sub-classes may override this, but should
     * call the super method in that case.
     * 校验配置是否正确。子类可以重写它，但是需要调用父类方法
     * 在 #bind(...) 方法中，绑定本地地址时，会调用该方法进行校验。
     */
    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * 克隆一个 AbstractBootstrap 对象
     * 实现自 Cloneable 接口，在子类中实现。这是深拷贝，即创建一个新对象，但不是所有的属性是深拷贝。
     * 浅拷贝属性：group、channelFactory、handler、localAddress 。
     * 深拷贝属性：options、attrs 。
     */
    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public abstract B clone();

    /**
     * Create a new {@link Channel} and register it with an {@link EventLoop}.
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * 创建一个 Channel， 并且绑定地址和端口
     * 有多个重载方法
     * 返回值是 ChannelFuture 对象，也就是异步的绑定端口，启动服务端。如果需要同步，则需要调用 ChannelFuture#sync() 方法。
     */
    public ChannelFuture bind() {
        // 校验服务启动需要的必要参数
        validate();
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    public ChannelFuture bind(SocketAddress localAddress) {
        // 校验服务启动需要的必要参数
        validate();
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress"));
    }

    /**
     * 绑定本地地址和端口
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 第一步：初始化并注册一个 Channel 对象，因为注册是异步的过程，所以返回一个 ChannelFuture 对象。
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {// 若发生异常，直接返回。
            return regFuture;
        }

        // 第二步：绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中。
        // 因为注册是异步的过程，有可能已完成，有可能未完成。所以分成 if-else 分别处理已完成和未完成的情况
        if (regFuture.isDone()) {
            // 已经注册完成
            // At this point we know that the registration was complete and successful.
            ChannelPromise promise = channel.newPromise();
            // doBind0 是核心的代码，用于绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中。
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // 注册还未完成
            // Registration future is almost always fulfilled already, but just in case it's not.
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            // 添加监听器，在注册完成后，进行回调执行 doBind0
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // 注册失败
                        // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                        // IllegalStateException once we try to access the EventLoop of the Channel.
                        promise.setFailure(cause);
                    } else {
                        // 注册成功
                        // Registration was successful, so set the correct executor to use.
                        // See https://github.com/netty/netty/issues/2586
                        promise.registered();
                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

    /**
     * 初始化并注册一个 Channel 对象。
     * 是异步注册
     * @return 返回一个 ChannelFuture
     */
    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            // 创建 Channel 对象
            channel = channelFactory.newChannel();
            // 初始化 Channel 配置
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {// 已创建 Channel 对象
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();// 强制关闭 Channel

                // 由于 channel 还没有注册，我们需要强制使用GlobalEventExecutor
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // 由于 channel 还没有注册，我们需要强制使用GlobalEventExecutor
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        // 注册 Channel 到 EventLoopGroup 中（实际在方法内部，EventLoopGroup 会分配一个 EventLoop 对象，将 Channel 注册到其上。）
        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            if (channel.isRegistered()) {
                // 若发生异常，并且 Channel 已经注册成功，则调用 #close() 方法，正常关闭 Channel 。
                channel.close();
            } else {
                // 若发生异常，并且 Channel 并未注册成功，则调用 #closeForcibly() 方法，强制关闭 Channel 。
                channel.unsafe().closeForcibly(); // 强制关闭 Channel
            }
        }

        // If we are here and the promise is not failed, it's one of the following cases:
        // 1) If we attempted registration from the event loop, the registration has been completed at this point.
        //    i.e. It's safe to attempt bind() or connect() now because the channel has been registered.
        // 2) If we attempted registration from the other thread, the registration request has been successfully
        //    added to the event loop's task queue for later execution.
        //    i.e. It's safe to attempt bind() or connect() now:
        //         because bind() or connect() will be executed *after* the scheduled registration task is executed
        //         because register(), bind(), and connect() are all bound to the same thread.

        //译：
        // 如果我们到这里 promise 没有失败，属于下列case之一
        // 1) 如果我们尝试从事件循环中注册，并且此时已经注册成功
        //    例如，现在尝试bind()或connect()是安全的，因为通道已经注册了。
        // 2) 如果我们尝试从另一个线程注册，注册请求已经成功地添加到事件循环的任务队列中，以供以后执行。
        //    例如，现在尝试bind()或connect()是安全的:
        //         因为bind()或connect()将在执行计划的注册任务之后执行
        //         因为register()、bind()和connect()都绑定到同一个线程。
        return regFuture;
    }

    abstract void init(Channel channel) throws Exception;

    // 绑定 Channel 的端口，并注册 Channel 到 SelectionKey 中。
    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up
        // the pipeline in its channelRegistered() implementation.
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     * 设置 Channel 的处理器
     */
    public B handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return self();
    }

    /**
     * 返回当前的 EventLoopGroup。使用 #config() 方法代替它
     */
    @Deprecated
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * 返回当前 AbstractBootstrap 的配置对象
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    final SocketAddress localAddress() {
        return localAddress;
    }

    @SuppressWarnings("deprecation")
    final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    final ChannelHandler handler() {
        return handler;
    }

    final Map<ChannelOption<?>, Object> options() {
        return copiedMap(options);
    }

    final Map<AttributeKey<?>, Object> attrs() {
        return copiedMap(attrs);
    }

    static <K, V> Map<K, V> copiedMap(Map<K, V> map) {
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<K, V>(map));
    }

    static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e: attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    /**
     * 设置传入的 Channel 的多个可选项
     * 不同于 option 方法，option 是设置要创建的 Channel 的可选项，而 此方法是设置已经创建的 Channel 的可选项
     */
    static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    static Map.Entry<AttributeKey<?>, Object>[] newAttrArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    static Map.Entry<ChannelOption<?>, Object>[] newOptionArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append('(').append(config()).append(')');
        return buf.toString();
    }

    static final class PendingRegistrationPromise extends DefaultChannelPromise {

        // Is set to the correct EventExecutor once the registration was successful. Otherwise it will
        // stay null and so the GlobalEventExecutor.INSTANCE will be used for notifications.
        private volatile boolean registered;

        PendingRegistrationPromise(Channel channel) {
            super(channel);
        }

        void registered() {
            registered = true;
        }

        @Override
        protected EventExecutor executor() {
            if (registered) {
                // If the registration was a success executor is set.
                //
                // See https://github.com/netty/netty/issues/2586
                return super.executor();
            }
            // The registration failed so we can only use the GlobalEventExecutor as last resort to notify.
            return GlobalEventExecutor.INSTANCE;
        }
    }
}
