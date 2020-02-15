package com.healthsaas.edge.safe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class HttpWebPortal {
    private static final String TAG = "SafeSDK." + HttpWebPortal.class.getSimpleName();
    static final int GET = 1;
    static final int POST = 2;
    private Context context;
    private HttpWebPortal.NetworkThread thread;

    HttpWebPortal(Context context) {
        this.context = context;
    }

    private boolean isNetworkConnected() {
        try {
            ConnectivityManager connMgr = (ConnectivityManager)this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return connMgr.getActiveNetworkInfo().isConnected();
        } catch (Throwable var2) {
            Log.d(TAG, "stop: " + var2.getMessage());
            return false;
        }
    }

    boolean get(long id, String url, Map<String, String> headers, HttpWebPortal.AsyncResponseCallback callback) {
        try {
            return this.thread.pushBack(new HttpWebPortal.Request(id, GET, url, headers, null, callback));
        } catch (Throwable var5) {
            Log.d(TAG, "stop: " + var5.getMessage());
            return false;
        }
    }

    boolean post(long id, String url, Map<String, String> headers, String data, HttpWebPortal.AsyncResponseCallback callback) {
        try {
            return this.thread.pushBack(new HttpWebPortal.Request(id, POST, url, headers, data, callback));
        } catch (Throwable var6) {
            Log.d(TAG, "stop: " + var6.getMessage());
            return false;
        }
    }

    private void flush() {
        try {
            if (this.thread != null)
                this.thread.flush();
        } catch (Throwable var2) {
            Log.d(TAG, "stop: " + var2.getMessage());
        }

    }

    void start() {
        try {
            this.stop();
            this.thread = new HttpWebPortal.NetworkThread();
            this.thread.start();
        } catch (Throwable var2) {
            Log.d(TAG, "stop: " + var2.getMessage());
        }

    }

    private void stop() {
        this.flush();

        try {
            if (this.thread != null)
                this.thread.quit();
        } catch (Throwable var2) {
            Log.d(TAG, "stop: " + var2.getMessage());
        }
        this.thread = null;
    }

    private class NetworkThread extends Thread {
        LinkedList<Request> queue = new LinkedList<>();
        boolean threadSleeping = false;
        boolean shouldQuit = false;

        NetworkThread() {
        }

        public void run() {
            while(!this.shouldQuit) {
                try {
                    HttpWebPortal.Request request = this.popFront();
                    if (request != null) {
                        HttpWebPortal.Response rsp;
                        switch(request.code) {
                            case GET:
                                rsp = this.sendGet(request.id, request.url, request.headers);
                                if (request.callback != null) {
                                    request.callback.onResponse(rsp);
                                }
                                break;
                            case POST:
                                rsp = this.sendPost(request.id, request.url, request.headers, request.data);
                                if (request.callback != null) {
                                    request.callback.onResponse(rsp);
                                }
                        }
                    }
                } catch (Throwable var3) {
                    Log.i(HttpWebPortal.TAG, "HttpWebPortal network thread exception!", var3);
                }
            }

        }

        void quit() {
            this.shouldQuit = true;
            this.pushFront(null);
        }

        synchronized void flush() {
            this.queue.clear();
        }

        synchronized boolean pushBack(HttpWebPortal.Request request) {
            boolean status = false;
            if (HttpWebPortal.this.isNetworkConnected()) {
                status = this.queue.add(request);
                if (this.threadSleeping) {
                    this.notify();
                    this.threadSleeping = false;
                }
            }
            return status;
        }

        synchronized boolean pushFront(HttpWebPortal.Request request) {
            boolean status = true;

            try {
                this.queue.add(0, request);
            } catch (Throwable var4) {
                status = false;
            }

            if (this.threadSleeping) {
                this.notify();
                this.threadSleeping = false;
            }

            return status;
        }

        synchronized HttpWebPortal.Request popFront() {
            while(this.queue.size() == 0 || !HttpWebPortal.this.isNetworkConnected()) {
                this.threadSleeping = true;

                try {
                    this.wait();
                } catch (Throwable var5) {
                    ;
                } finally {
                    this.threadSleeping = false;
                }
            }

            return this.queue.removeFirst();
        }

        private HttpWebPortal.Response sendGet(long id, String url, Map<String, String> headers) {
            HttpURLConnection conn = null;
            String rspData = null;
            int responseCode = 0;
            boolean status = false;

            try {
                if (!HttpWebPortal.this.isNetworkConnected()) {
                    return new HttpWebPortal.Response(id, GET);
                }

                URL obj = new URL(url);
                conn = (HttpURLConnection)obj.openConnection();
                conn.setRequestMethod("GET");
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                this.addHeaders(conn, headers);
                conn.connect();
                responseCode = conn.getResponseCode();
                rspData = this.readInput(conn.getInputStream());
            } catch (Throwable var8) {
                Log.e(HttpWebPortal.TAG, "GET exception!", var8);
            }

            if (conn != null) {
                conn.disconnect();
            }

            status = responseCode >= 200 && responseCode < 300;
            return new HttpWebPortal.Response(id, GET, url, status, responseCode, rspData);
        }

        private HttpWebPortal.Response sendPost(long id, String url, Map<String, String> headers, String data) {
            HttpURLConnection conn = null;
            String rspData = null;
            int responseCode = 0;
            boolean status = false;

            try {
                if (!HttpWebPortal.this.isNetworkConnected()) {
                    return new HttpWebPortal.Response(id, POST);
                }

                URL obj = new URL(url);
                conn = (HttpURLConnection)obj.openConnection();
                conn.setRequestMethod("POST");
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                this.addHeaders(conn, headers);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(data.length());
                conn.connect();
                this.writeOutput(conn.getOutputStream(), data);
                responseCode = conn.getResponseCode();
                rspData = this.readInput(conn.getInputStream());
            } catch (Throwable var9) {
                Log.e(HttpWebPortal.TAG, "POST exception!", var9);
            }

            if (conn != null) {
                conn.disconnect();
            }

            status = responseCode >= 200 && responseCode < 300;
            return new HttpWebPortal.Response(id, POST, url, status, responseCode, rspData);
        }

        private void addHeaders(HttpURLConnection conn, Map<String, String> headers) {
            if (headers != null) {
                Set<Entry<String, String>> set = headers.entrySet();
                Iterator it = set.iterator();

                while(it.hasNext()) {
                    Entry<String, String> header = (Entry)it.next();
                    conn.setRequestProperty((String)header.getKey(), (String)header.getValue());
                }
            }

        }

        private void writeOutput(OutputStream stream, String data) {
            DataOutputStream writer = null;

            try {
                writer = new DataOutputStream(stream);
                writer.writeBytes(data);
                writer.flush();
            } catch (Throwable var6) {
                ;
            }

            try {
                writer.close();
            } catch (Throwable var5) {
                ;
            }

        }

        private String readInput(InputStream stream) {
            BufferedReader reader = null;
            String data = null;

            try {
                StringBuffer buffer = new StringBuffer();
                reader = new BufferedReader(new InputStreamReader(stream));

                String line;
                while((line = reader.readLine()) != null) {
                    buffer.append(line);
                }

                data = buffer.toString();
            } catch (Throwable var7) {
                ;
            }

            try {
                reader.close();
            } catch (Throwable var6) {
                ;
            }

            return data;
        }
    }

    public interface AsyncResponseCallback {
        void onResponse(HttpWebPortal.Response response);
    }

    public static class Response {
        private long id;
        private boolean status;
        private int requestCode;
        private String url;
        private String data;
        private int responseCode;

        private Response(long id, int requestCode) {
            this(id, requestCode, (String)null, false, 0, (String)null);
        }

        private Response(long id, int requestCode, String url, boolean status, int responseCode, String data) {
            this.id = id;
            this.requestCode = requestCode;
            this.url = url;
            this.status = status;
            this.responseCode = responseCode;
            this.data = data;
        }

        public long getId() {
            return this.id;
        }

        public int getResponseCode() {return this.responseCode;}

        public int getRequestCode() {return this.requestCode;}

        public boolean httpRequestSucceeded() {
            return this.status;
        }

        public String getUrl() {
            return this.url;
        }

        public String getData() {
            return this.data;
        }
    }

    public static class Request {
        long id;
        int code;
        String url;
        String data;
        Map<String, String> headers;
        HttpWebPortal.AsyncResponseCallback callback;

        Request(long id, int code, String url, Map<String, String> headers, String data, HttpWebPortal.AsyncResponseCallback callback) {
            this.id = id;
            this.code = code;
            this.url = url;
            this.headers = headers;
            this.data = data;
            this.callback = callback;
        }
    }
}
