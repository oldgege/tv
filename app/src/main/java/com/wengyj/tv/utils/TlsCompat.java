package com.wengyj.tv.utils;

import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Android 4.x（API 16~19）HTTPS 兼容层。
 *
 * <p>问题背景：Android 4.3 及更早版本创建的 {@link SSLSocket} 默认只启用
 * SSLv3 / TLSv1，且部分机型不会自动带 SNI。而 jsDelivr、GitHub 等站点已
 * 强制要求 TLS1.2 + SNI，服务端在 ServerHello 阶段就会返回
 * <code>sslv3 alert handshake failure</code>，表现为：</p>
 *
 * <pre>
 * javax.net.ssl.SSLProtocolException: SSL handshake aborted:
 *   error:14077410:SSL routines:SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure
 * </pre>
 *
 * <p>解决办法：在 Application 启动时 {@link #install()} 一次，为进程内所有
 * HTTPS 连接装上兼容工厂——握手前打开 TLS1.1/TLS1.2（并禁用不安全的 SSLv3），
 * 同时用反射补上 SNI 主机名。</p>
 */
public final class TlsCompat {

    private static final String TAG = "TlsCompat";

    /** 需要显式打开的协议，按安全强度从高到低排列 */
    private static final String[] PREFERRED_PROTOCOLS = {"TLSv1.2", "TLSv1.1", "TLSv1"};

    /** 明确禁用的不安全协议 */
    private static final String[] BANNED_PROTOCOLS = {"SSLv3", "SSLv2Hello"};

    private static volatile SSLSocketFactory FACTORY;

    private TlsCompat() {
    }

    /**
     * 安装兼容工厂，建议在 {@link android.app.Application#onCreate()} 中调用一次。
     * 安装后进程内所有 HTTPS（含 HTTP → HTTPS 重定向）都会生效。
     */
    public static void install() {
        if (FACTORY != null) {
            return;
        }
        synchronized (TlsCompat.class) {
            if (FACTORY != null) {
                return;
            }
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, null, null);
                FACTORY = new CompatSslSocketFactory(ctx.getSocketFactory());
                HttpsURLConnection.setDefaultSSLSocketFactory(FACTORY);
                Log.i(TAG, "已启用 TLS1.1/1.2 + SNI 兼容（Android 4.x）");
            } catch (Throwable t) {
                Log.w(TAG, "TLS 兼容初始化失败: " + t.getMessage());
            }
        }
    }

    /** 为单个连接套用兼容工厂，保证重定向后的连接同样生效 */
    public static void apply(HttpURLConnection conn) {
        if (conn == null || !(conn instanceof HttpsURLConnection)) {
            return;
        }
        install();
        if (FACTORY != null) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(FACTORY);
        }
    }

    /**
     * 计算最终启用的协议：在「设备支持」的基础上，打开 TLS1.1/1.2，
     * 并剔除 SSLv3 / SSLv2Hello。
     */
    private static String[] resolveProtocols(String[] supported, String[] enabled) {
        List<String> result = new ArrayList<String>();
        if (supported != null) {
            for (String p : PREFERRED_PROTOCOLS) {
                if (contains(supported, p)) {
                    result.add(p);
                }
            }
            // 结果为空说明设备不认识上面的协议名，退回系统默认启用的协议
            if (result.isEmpty() && enabled != null) {
                for (String p : enabled) {
                    if (!contains(BANNED_PROTOCOLS, p)) {
                        result.add(p);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            return enabled;
        }
        return result.toArray(new String[result.size()]);
    }

    private static boolean contains(String[] array, String value) {
        if (array == null) {
            return false;
        }
        for (String item : array) {
            if (value.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 老版本系统没有公开 SNI 设置接口，通过反射调用
     * OpenSSLSocketImpl#setHostname 补上主机名。
     */
    private static void setSniHostname(SSLSocket socket, String host) {
        if (host == null) {
            return;
        }
        try {
            Method m = socket.getClass().getMethod("setHostname", String.class);
            m.invoke(socket, host);
        } catch (Throwable ignored) {
            // 机型不支持时静默跳过
        }
    }

    private static final class CompatSslSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;

        CompatSslSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
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
            return patch(delegate.createSocket(), null);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            return patch(delegate.createSocket(host, port), host);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            return patch(delegate.createSocket(host, port, localHost, localPort), host);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port), null);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                                   int localPort) throws IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort), null);
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose)
                throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose), host);
        }

        private Socket patch(Socket socket, String host) {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                ssl.setEnabledProtocols(
                        resolveProtocols(ssl.getSupportedProtocols(), ssl.getEnabledProtocols()));
                setSniHostname(ssl, host);
            }
            return socket;
        }
    }
}
