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

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;


/**
 * ChannelFuture 代表异步I/O操作的结果。
 *
 * ⭐ <h3>第一部分：ChannelFuture的状态</h3>
 * Netty中的所有I/O操作都是异步的，也就是说任何I/O调用都将立即返回，并且你无法确保I/O操作在调用结束时已经完成。
 * 那么我们该如何获取异步操作的执行结果呢？
 * 在Java中，我们可以通过Future#get方法，在未来的某个时候阻塞的获取这个IO操作的执行结果。而在Netty中，可以使用Netty扩展的ChannelFuture接口，通过监听器回调的方式，更高效且优雅地获取操作结果和操作状态（未完成、完成且成功、完成且失败、操作被取消）。
 * ChannelFuture的状态要么是成功，要么是失败。当一个I/O操作开始时，会创建一个新的future对象。这个新future最初的状态是未完成（既不是成功，也不是失败，也不是取消）。如果这个I/O操作完成（包括操作成功、操作失败、操作取消），状态会被标记为已完成。
 * ChannelFuture 提供了多个方法用于检查IO操作是否已完成，查询操作的结果。除此之外，它还支持添加多个监听器（ChannelFutureListener），当IO操作完成时，这些监听器会收到通知。
 * 下面是ChannelFuture状态的迁移图
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 *
 * ⭐ <h3>第二部分：使用监听器代替await</h3>
 * Netty推荐使用添加监听器的方式来代替使用await方式，来获取异步IO操作的结果。
 * addListener(GenericFutureListener)方法是非阻塞的，它只是添加指定的ChannelFutureListener到这个channelFuture上，当IO操作关联的future完成时，IO线程会通知这些监听器。这种方式是非常高效的，资源利用率也很低，因为它不会阻塞线程。
 * 相比之下，await方法是一个阻塞操作。一旦调用，调用者线程会阻塞直到操作完成。虽然使用这种方式实现顺序逻辑比较容易，但是调用线程会在I/O操作完成之前不必要地阻塞，并且线程间通知的开销相对较高。此外，在特定的情况下也有死锁的可能，所以也引出下一条建议：不要在ChannelHandler中调用await。
 *
 *
 * ⭐ <h3>第三部分：不要在ChannelHandler中调用await方法</h3>
 * 因为在 ChannelHandler 中事件处理的方法，通常是被IO线程调用的。如果在 ChannelHandler 中调用 await 方法，实际上是把这个IO线程给阻塞了，那么这个IO操作可能永远不会完成，这样相当于自己把自己阻塞了，就形成了死锁。
 * <pre>
 * // 错误案例 - 永远不要这么写
 * public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *     ChannelFuture future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 * // 正确案例
 * public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *     ChannelFuture future = ctx.channel().close();
 *     // 添加监听器，当操作完成时会收到通知，执行 operationComplete 方法
 *     future.addListener(new ChannelFutureListener() {
 *         public void operationComplete(ChannelFuture future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * 尽管存在上述缺点，但在某些情况下，调用await方法会更加方便。
 * 但请确保不要在I/O线程中调用，否则会抛出BlockingOperationException以防止死锁
 *
 *
 * ⭐ <h3>第四部分：不要混淆I/O超时和等待超时</h3>
 * 使用带超时时间的 await、awaitUninterruptibly 方法时，指定的超时时间和IO超时完全无关。
 * 如果是IO超时，future会被标记为 Completed with failure。
 * 例如：应该通过传输特定的option来设置连接超时
 *
 * <pre>
 * // 错误案例 - 永远不要这么写
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // 正确案例
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // 现在我们确信future已经完成
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * 返回与这个Future关联的channel对象
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * 如果这个Future是一个 void future，返回true。
     * 此时不允许调用以下任何方法
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}
