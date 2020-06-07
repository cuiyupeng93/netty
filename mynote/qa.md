# QA

> 记录学习 Netty 过程中遇到的问题与答案。

## 启动器篇

**1. ServerBootstrap 启动服务端时序图**

参见《Netty 权威指南》P260，代码案例可见 netty 源码中的 EchoServer。


**2. ServerBootstrap 通过反射创建 Channel 对性能有影响吗？**

由于服务端监听端口只需要在系统启动时调用，因此反射对性能影响并不大。





## EventLoopGroup 
