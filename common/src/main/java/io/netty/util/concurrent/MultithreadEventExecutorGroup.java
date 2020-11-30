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
package io.netty.util.concurrent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 *
 * 一个实现 EventExecutorGroup 接口的抽象类，并且是以多线程方式处理它们的任务
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    /**
     * EventExecutor 数组
     */
    private final EventExecutor[] children;
    /**
     * 不可变( 只读 )的 EventExecutor 数组
     */
    private final Set<EventExecutor> readonlyChildren;
    /**
     * 已终止的 EventExecutor 数量
     */
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    /**
     * 用于终止 EventExecutor 的异步 Future
     */
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    /**
     * EventExecutor 选择器
     */
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * 构造方法
     *
     * @param nThreads          线程数
     * @param threadFactory     使用的线程工厂，为null时会使用默认的
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * 构造方法
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        // 第三个参数是选择器工厂对象
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    // 构造方法
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        // 1. 创建线程池 executor
        // 注：这个线程池是后边给 eventLoop 使用的, eventLoop 第一次提交任务时，会发现还没有绑定线程，所以会绑定到这个线程池创建出来的线程上
        if (executor == null) {
            // 看名字ThreadPerTaskExecutor可以发现，这是每个任务都会开启一个线程的线程池。
            // 由于是从子类调用过来的，所以newDefaultThreadFactory方法会走到子类重写的方法中
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        // 2. 创建事件执行器数组children，大小就是传入的nThreads
        children = new EventExecutor[nThreads];

        // 3. 循环创建nThreads个EventLoop对象，填充到children中
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;// 是否创建成功
            try {
                // 调用newChild方法创建EventExecutor对象
                // 如果是从NioEventLoopGroup的构造方法调用上来的，这里会调用NioEventLoopGroup#newChild方法
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                // 创建失败，关闭所有已创建的 EventExecutor
                if (!success) {
                    // 关闭所有已创建的 EventExecutor
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }
                    // 确保所有已创建的 EventExecutor 已关闭
                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        // 4. 创建选择器对象chooser
        chooser = chooserFactory.newChooser(children);

        // 5. 创建监听器，用于监听到EventLoop终止时，进行回调
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                // 回调的具体逻辑是，当所有的EventExecutor全部终止完成时，通过调用 Future#setSuccess(V result) 方法，通知监听器们。
                // 至于为什么设置的值是 null ，因为监听器们不关注具体的结果。
                if (terminatedChildren.incrementAndGet() == children.length) {// 全部关闭
                    terminationFuture.setSuccess(null);// 设置结果，并通知监听器们
                }
            }
        };

        // 6. 将监听器绑定到每个EventExecutor上
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        // 7. 创建一个只读的EventExecutor数组：readonlyChildren
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    /**
     * 创建线程工厂对象
     * @return
     */
    protected ThreadFactory newDefaultThreadFactory() {
        // DefaultThreadFactory 是 netty 自己实现的默认线程池工厂
        return new DefaultThreadFactory(getClass());
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    /**
     * 获得 EventExecutor 数组的迭代器
     * 为了避免调用方，获得迭代器后，对 EventExecutor 数组进行修改，所以返回是不可变的 EventExecutor 数组 readonlyChildren 的迭代器。
     * @return
     */
    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     *
     * 获得 EventExecutor 数组的大小
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     * 创建 EventExecutor 对象。抽象方法，由子类实现（在 NioEventLoopGroup 中实现）
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    /**
     * 优雅关闭
     */
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }
}
