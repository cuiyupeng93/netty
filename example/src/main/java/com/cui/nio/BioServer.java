package com.cui.nio;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务端程序
 */
public class BioServer {

    private static ExecutorService pool = Executors.newFixedThreadPool(5);

    public synchronized static void start(int port) throws IOException {
        ServerSocket server = new ServerSocket(port);
        System.out.println("服务端已启动, 端口号:" + port);
        //循环监听客户端的连接
        while (true) {
            //等待客户端连接, 没有接收到连接时将阻塞
            Socket client = server.accept();
            pool.execute(new BioServer().new ServerHandler(client));
        }
    }

    //处理客户端请求的线程类
    private class ServerHandler implements Runnable {
        private Socket client;

        public ServerHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            BufferedReader in = null;
            PrintStream out = null;
            try {
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out = new PrintStream(client.getOutputStream());
                String readData;
                while ((readData = in.readLine()) != null) {
                    System.out.println(readData);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close(in, out, client);
            }
        }

        private void close(Closeable... closeableArr) {
            if (closeableArr != null) {
                for (Closeable closeable : closeableArr) {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}

