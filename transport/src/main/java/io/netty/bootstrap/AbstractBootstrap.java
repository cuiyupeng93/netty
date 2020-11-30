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

    /**
     * 构造方法
     * @param bootstrap
     */
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
     * 创建一个 Channel，绑定端口
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

    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 1. 初始化 Channel 操作，都在initAndRegister方法中
        // 主要是：创建Channel、初始化Channel、Channel注册到EventLoop，其中注册是异步流程，会返回一个regFuture代表注册流程的结果
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.cause() != null) {
            // 如果cause能获取到值，说明注册流程失败了
            return regFuture;
        }

        // 2. doBind0操作
        // doBind0是核心代码，用于设置Channel绑定端口，并注册到SelectionKey中。
        // 因为"Channel注册到EventLoop"是异步操作，执行到此不能确定操作是否已完成，所以这里使用if-else判断。最终都会执行到doBind0方法
        if (regFuture.isDone()) {
            // "Channel注册到EventLoop" 操作已完成，直接调用doBind0
            // 注意：完成不代表成功，可能是成功、失败、取消，doBind0里会根据这个promise的状态得知注册操作的结果
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // 此时Channel尚未初始化完成，注册一个监听器，等它完成后回调执行doBind0
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            // 添加监听器，在"Channel注册到EventLoop"这个操作完成后，进行回调执行 doBind0
            // （更具体的说，就是AbstractChannel.AbstractUnsafe#register0方法中的safeSetSuccess执行后，就会通知回调此监听器）
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        // 注册失败
                        promise.setFailure(cause);
                    } else {
                        // 注册成功，执行doBind0方法
                        promise.registered();
                        doBind0(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

    /**
     * 初始化Channel流程
     */
    final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            // 1. 通过反射创建Channel对象
            channel = channelFactory.newChannel();
            // 2. 初始化Channel配置
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                // 如果Channel已经创建好了，需要强制关闭它，避免资源浪费
                channel.unsafe().closeForcibly();
                // 由于出现异常了，Channel还没有执行到注册流程，所以还没有和一个eventLoop绑定，
                // 但是返回的DefaultChannelPromise里又需要一个执行器对象，来执行通知监听器、唤醒线程等操作，
                // 所以这里使用了一个 GlobalEventExecutor.INSTANCE
                // 并在最后setFailure，也就是告诉上层方法，初始化Channel失败了，失败原因封装在当前异常对象里
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
            }
            // 如果Channel还没有创建，直接返回一个DefaultChannelPromise，并setFailure
            return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
        }

        // 3. Channel注册到EventLoopGroup中的一个eventLoop上（其实是将eventLoop的引用赋值到这个AbstractChannel#eventLoop上）
        // 这一步是异步操作，返回一个ChannelFuture
        ChannelFuture regFuture = config().group().register(channel);
        if (regFuture.cause() != null) {
            // cause不为null，说明注册失败了
            if (channel.isRegistered()) {
                // 若发生异常，并且Channel已经注册成功，则正常关闭Channel
                channel.close();
            } else {
                // 若发生异常，并且Channel并未注册成功，则强制关闭Channel
                channel.unsafe().closeForcibly();
            }
        }
        return regFuture;
    }

    /**
     * 初始化 Channel 配置，由子类实现
     * @param channel
     * @throws Exception
     */
    abstract void init(Channel channel) throws Exception;

    // 设置Channel绑定端口，并注册到SelectionKey中。
    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {

        // doBind0向EventLoop任务队列中添加一个bind任务来完成后续操作
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    // 绑定本地端口，并添加一个CLOSE_ON_FAILURE监听器
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    // 如果初始化channel失败，这里直接设为失败
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
