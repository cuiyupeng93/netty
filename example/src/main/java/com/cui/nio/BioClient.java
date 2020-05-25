package com.cui.nio;

import java.io.*;
import java.net.Socket;

/**
 * 客户端程序
 */
public class BioClient {
    public static void send(String ip, int port, String word) {
        Socket client = null;
        BufferedReader in = null;
        PrintStream out = null;
        try {
            //创建客户端Socket, 指定服务端的ip与端口号
            client = new Socket(ip, port);

            //获得对于服务端的输入输出流
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintStream(client.getOutputStream());
            out.println(word);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(in, out, client);
        }
    }

    private static void close(Closeable... closeableArr) {
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