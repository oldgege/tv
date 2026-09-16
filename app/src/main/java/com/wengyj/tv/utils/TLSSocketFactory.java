package com.wengyj.tv.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 强制开启 TLS 1.2 的 SocketFactory，解决 Android 4.3 握手失败问题。
 */
public class TLSSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;

    public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        delegate = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(delegate.createSocket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                               int localPort) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(address, port, localAddress, localPort));
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose));
    }

    private Socket enableTLSOnSocket(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            String[] supported = sslSocket.getSupportedProtocols();
            List<String> enabled = new ArrayList<>();
            // 优先启用 TLS 1.2，其次 1.1，最后保留 1.0
            if (contains(supported, "TLSv1.2")) {
                enabled.add("TLSv1.2");
            }
            if (contains(supported, "TLSv1.1")) {
                enabled.add("TLSv1.1");
            }
            if (enabled.isEmpty() && contains(supported, "TLSv1")) {
                enabled.add("TLSv1");
            }
            if (!enabled.isEmpty()) {
                sslSocket.setEnabledProtocols(enabled.toArray(new String[0]));
            }
        }
        return socket;
    }

    private static boolean contains(String[] array, String value) {
        if (array == null) return false;
        for (String item : array) {
            if (value.equalsIgnoreCase(item)) return true;
        }
        return false;
    }
}