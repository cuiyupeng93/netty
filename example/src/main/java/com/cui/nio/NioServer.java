package com.cui.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

/**
 * 多人聊天室 - 服务端程序
 * 实现功能: 接收某个客户端的消息, 并广播给其他所有客户端
 * <p>
 * 服务端Selector仅需要监听两种事件:新连接到来(Accept)和有数据可读(Read).
 * 1. 处理Accept事件时, 系统需要接受客户端的连接, 并将其注册到Selector上
 * 2. 处理Read事件时, 系统需要读取客户端的消息, 再将消息输出到Selector上注册的所有SocketChannel中
 *
 * @author cuiyupeng
 */
public class NioServer {
    private static final String IP = "192.168.1.104";
    private static final int PORT = 12306;

    public static void main(String[] args) {
        new Thread(new NioServer().new NServerHandler(IP, PORT), "Server").start();
    }

    //成员内部类
    private class NServerHandler implements Runnable {
        private String ip;
        private int port;
        private Selector selector;
        private ServerSocketChannel serverChannel;
        private Charset charset = Charset.forName("utf-8");

        public NServerHandler(String ip, int port) {
            this.ip = ip;
            this.port = port;
            try {
                //创建一个Selector
                selector = Selector.open();
                //创建一个ServerSocketChannel
                serverChannel = ServerSocketChannel.open();
                //将ServerSocketChannel绑定到指定IP与端口
                serverChannel.socket().bind(new InetSocketAddress(ip, port));
                //设置ServerSocketChannel为非阻塞模式
                serverChannel.configureBlocking(false);
                //将ServerSocketChannel注册到selector上, 事件类型为接收新连接
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                //循环查询就绪的Channel数量（会阻塞等待）
                while (selector.select() > 0) {
                    //获取就绪的Channel的SelectionKey集合
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    //遍历该集合, 依次处理每个Channel
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while (iterator.hasNext()) {
                        //每处理一个Channel, 就从selected-key集合中删除对应的SelectionKey, 因为Selector不会自动清除
                        iterator.remove();
                        //处理每个SelectionKey对应的Channel的事件
                        dispatch(iterator.next());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //处理每个SelectionKey对应的Channel的事件
        private void dispatch(SelectionKey selectionKey) throws IOException {
            try {
                if (selectionKey.isValid()) {
                    if (selectionKey.isAcceptable()) {
                        //如果是Accept事件: 代表该Channel接收到新连接
                        register(selectionKey);
                    } else if (selectionKey.isReadable()) {
                        //如果是Read事件: 代表该Channel有数据可以读
                        read(selectionKey);
                    } else if (selectionKey.isWritable()) {
                        write(selectionKey);
                    }
                }
            } catch (Exception e) {
                //在处理客户端SocketChannel时, 如果客户端关闭, 会导致服务端处理客户端Channel
                //的代码抛出异常, 所以需要捕获异常, 并取消该客户端Channel在selector上的注册
                //关闭资源
                e.printStackTrace();
                if (selectionKey != null) {
                    selectionKey.cancel();//取消该Channel与Selector的注册关系
                    if (selectionKey.channel() != null) {
                        selectionKey.channel().close();//关闭该通道
                    }
                }
            }
        }

        private void register(SelectionKey selectionKey) throws Exception {
            //显然只有ServerSocketChannel支持Accept事件, 所以该Channel就是serverChannel
            //调用accept方法接受连接, 并产生服务端的SocketChannel对象
            //(完成该操作也意味着已经建立了TCP连接)
            SocketChannel clientChannel = serverChannel.accept();
            //将新接入的clientChannel设置使用非阻塞模式
            clientChannel.configureBlocking(false);
            //将新接入的clientChannel注册到selector对象上, 并监听其Read事件
            clientChannel.register(selector, SelectionKey.OP_READ);
            //将该新接入的clientChannel的感兴趣的事件重新设置为Accept
            selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        }

        private void read(SelectionKey selectionKey) throws Exception {
            //获取该selectionKey对应的Channel
            SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
            //分配一个容量为1M的非直接缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            //循环读取数据, 因为该Channel是非阻塞模式的(注册在selector上的channel都应该是非阻塞模式的)
            //所以其read, write方法都不会使当前线程阻塞, 所以有可能只读了一部分数据就返回了,所以要放在while循环中0
            StringBuffer message = new StringBuffer();//算术表达式字符串
            while (clientChannel.read(buffer) > 0) {
                buffer.flip();//切换到读模式
                message.append(charset.decode(buffer));
                buffer.clear();
            }
            System.out.println("服务器收到的消息: " + message.toString());
            //重新设定该Channel对Read事件感兴趣
            selectionKey.interestOps(SelectionKey.OP_READ);
            //向所有客户端广播消息
            writeToAllSocketChannel(message.toString());
        }

        private void write(SelectionKey selectionKey) throws Exception {
            //略
        }

        //向所有SocketChannel输出这条消息
        private void writeToAllSocketChannel(String message) throws IOException {
            if (message.length() > 0) {
                //获取所有注册在该selector上的channel的SelectionKey集合
                Set<SelectionKey> keys = selector.keys();
                //遍历该集合
                for (SelectionKey key : keys) {
                    //获取该SelectionKey对应的channel
                    SelectableChannel targetChannel = key.channel();
                    //如果是SocketChannel, 就输出消息
                    if (targetChannel instanceof SocketChannel) {
                        ((SocketChannel) targetChannel).write(charset.encode(message));
                    }
                }
            }
        }
    }
}