package com.cui.nio;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
/**
 * 多人聊天室 - 客户端程序
 * 实现功能: 从键盘接收用户输入的数据, 发送给服务器; 接收服务器广播的其他用户的消息.
 *
 * 客户端需要两个线程:
 * 一个线程负责读取用户的键盘输入, 并将输入的内容写入SocketChannel中;
 * 另一个线程负责不断的调用Selector.select()方法, 查询是否有channel就绪, 有则处理它
 * (客户端程序只需要一个channel就行了, 所以Selector.select()方法只有0和1两种结果)
 * @author cuiyupeng
 *
 */
public class NioClient {
    private static final String IP = "192.168.1.104";
    private static final int PORT = 12306;
    private Charset charset = Charset.forName("utf-8");
    private Selector selector;
    private SocketChannel clientChannel;

    public void init() {
        try {
            selector = Selector.open();
            clientChannel = SocketChannel.open(new InetSocketAddress(IP, PORT));
//                clientChannel.bind(new InetSocketAddress("localhost", PORT));
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            new Thread(new NClientHandler(), "NClientHandler").start();
            new Thread(new InputHandler(), "InputHandler").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //成员内部类, 接收用户从键盘输入数据的线程类
    private class InputHandler implements Runnable {
        @Override
        public void run() {
            try {
                //从键盘输入消息, 写出到clientChannel中
                Scanner scan = new Scanner(System.in);
                while (scan.hasNextLine()) {
                    String line = scan.nextLine();
                    clientChannel.write(charset.encode(line));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    //成员内部类, 读取服务端广播的消息的线程类
    private class NClientHandler implements Runnable {
        @Override
        public void run() {
            try {
                while (selector.select() > 0) {
                    //获取Selector上所有就绪的channel的SelectionKey集合
                    Set<SelectionKey> keys = selector.selectedKeys();
                    //遍历该集合
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = (SelectionKey) iterator.next();
                        iterator.remove();//每处理一个SelectionKey, 就从集合中删除一个
                        //如果该Channel触发了"有数据可读"的事件, 读取数据
                        if (key.isReadable()) {
                            SocketChannel clientChannel = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            StringBuffer message = new StringBuffer();
                            while (clientChannel.read(buffer) > 0) {
                                buffer.flip();
                                message.append(charset.decode(buffer));
                                buffer.clear();
                            }
                            System.out.println("聊天信息: " + message);
                            //重新设定该Channel对Read事件感兴趣
                            key.interestOps(SelectionKey.OP_READ);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        new NioClient().init();
    }

}