/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import java.util.concurrent.TimeUnit;


/**
 * The result of an asynchronous operation.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * 当且仅当I/O操作完成且成功时，返回true。
     */
    boolean isSuccess();

    /**
     * 是否可以取消
     * 当且仅当可以通过{@link#cancel（boolean）}取消操作时，返回{@code true}。
     */
    boolean isCancellable();

    /**
     * 如果I/O操作失败，则返回I/O操作失败的原因。
     *
     * @return 返回失败的原因。如果操作成功完成，或者此future尚未完成，返回null。
     */
    Throwable cause();

    /**
     * 添加事件监听器。
     * 当操作完成时，添加的监听器会收到通知。
     * 如果添加时已经完成了，则会立即通知指定的监听器。
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 一次添加多个事件监听器
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 移除一个监听器
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 移除多个监听器
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 同步等待操作完成，如果操作失败，会返回失败的原因
     * 这个方法会抛出 InterruptedException 异常
     */
    Future<V> sync() throws InterruptedException;

    /**
     * 同步等待操作完成，如果操作失败，会返回失败的原因
     */
    Future<V> syncUninterruptibly();

    /**
     * 等待这个Future完成
     */
    Future<V> await() throws InterruptedException;

    /**
     * 等待这个Future完成，此方法捕获InterruptedException异常，并静默丢弃它
     * todo qa 2020-11-10 意思是不能中断这个线程？
     */
    Future<V> awaitUninterruptibly();

    /**
     * 等待这个Future完成，指定最多等待的时间限制。
     *
     * @return 当这个Future在指定的时间内完成时，返回true
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 等待这个Future完成，指定最多等待的时间限制。
     *
     * @return 当这个Future在指定的时间内完成时，返回true
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * 等待这个Future完成，指定最多等待的时间限制。
     * 这个方法会捕获InterruptedException异常，并且静默丢弃
     *
     * @return 当这个Future在指定的时间内完成时，返回true
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * 等待这个Future完成，指定最多等待的时间限制。
     * 这个方法会捕获InterruptedException异常，并且静默丢弃
     *
     * @return 当这个Future在指定的时间内完成时，返回true
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * 直接返回现在的结果。如果这个Future还没有完成，会返回null。
     * 不要依赖这个null来判断操作是否完成了，您还需要检查这个Future是否真的完成了（用isDone方法）
     */
    V getNow();

    /**
     * 取消这个Future
     * 如果取消成功，将会使这个Future操作失败，并且出现CancellationException异常
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
