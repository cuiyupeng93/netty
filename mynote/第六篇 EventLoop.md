# 第六篇 EventLoop & EventLoopGroup

## 未整理的知识点

EventLoopGroup 实际是一个 Reactor 线程池。负责调度和执行客户端的接入、网络读写事件的处理、用户自定义任务和定时任务。

EventLoopGroup 是 EventLoop 的数组

