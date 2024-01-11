package com.jd.mpc.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Http工具
 *
 * @author sunxiaopeng9
 */
@Slf4j
public class HttpUtil {

    /**
     * POST 请求
     *
     * @param url 地址
     * @param data 请求数据，json格式
     * @return response
     */
    public static String post(String url, String data, Integer timeout,
            Map<String, String> header) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            // 发送POST请求必须设置为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // 设置连接超时时间和读取超时时间
            if (timeout == null) {
                conn.setConnectTimeout(5000);
            }
            else {
                conn.setConnectTimeout(timeout);
            }
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (header != null) {
                header.forEach(conn::setRequestProperty);
            }
            // 获取输出流
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
            out.close();
            // 取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }
    public static String get(String url, String data, Integer timeout,
                              Map<String, String> header) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            // 发送POST请求必须设置为true
            conn.setDoOutput(false);
            conn.setDoInput(false);
            // 设置连接超时时间和读取超时时间
            if (timeout == null) {
                conn.setConnectTimeout(5000);
            }
            else {
                conn.setConnectTimeout(timeout);
            }
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (header != null) {
                header.forEach(conn::setRequestProperty);
            }
            // 获取输出流
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
            out.close();
            // 取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }
    public static String formPost(String url, Map<String, String> param) {
        String body = null;
        try {
            //设置客户端编码
//            if (httpClient == null) {
            HttpClient httpClient = new DefaultHttpClient();
//            }
            // Post请求
            HttpPost httppost = new HttpPost(url);
            //设置超时时间
            httppost.getParams().setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");

            // 设置参数
            List<NameValuePair> data = new ArrayList<NameValuePair>();
            //封装参数
            Set<String> keys = param.keySet();

            for (String key : keys) {
                String value = param.get(key);
                NameValuePair item = new BasicNameValuePair(key, value);
                data.add(item);
            }

            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(data, "UTF-8");

            httppost.setEntity(formEntity);

            // 发送请求
            HttpResponse httpresponse = httpClient.execute(httppost);

            if (httpresponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                HttpEntity entity = httpresponse.getEntity();
                body = EntityUtils.toString(entity, "utf-8");
                if (entity != null) {
                    entity.consumeContent();
                }
            }
        } catch (Exception e) {
            log.error("异常信息：", e);
        }
        return body;
    }

    /**
     * POST 请求
     *
     * @param url 地址
     * @param data 请求数据，json格式
     * @return response
     */
    public static String postSSL(String url, String data, Integer timeout,
            Map<String, String> header) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpsURLConnection conn;
        try {
            TrustManager[] tm = {
                new MyX509TrustManager()
            };
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, tm, new SecureRandom());
            SSLSocketFactory ssf = sslContext.getSocketFactory();
            conn = (HttpsURLConnection) new URL(url).openConnection();
            conn.setHostnameVerifier(new TrustAnyHostnameVerifier());
            conn.setRequestMethod("POST");
            conn.setSSLSocketFactory(ssf);
            // 发送POST请求必须设置为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // 设置连接超时时间和读取超时时间
            if (timeout == null) {
                conn.setConnectTimeout(5000);
            }
            else {
                conn.setConnectTimeout(timeout);
            }
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (header != null) {
                header.forEach(conn::setRequestProperty);
            }
            // 获取输出流
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
            out.close();
            // 取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }

    // 服务器主机名校验
    public static class TrustAnyHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    /**
     * GET 请求
     *
     * @param url 地址
     * @return response
     */
    public static String get(String url) {
        return get(url, 5000, 60000);
    }
    public static String get(String url,Map<String, String> header){
        int connectTimeout=5000;
        int readTimeout = 5000;
        HttpURLConnection conn = null;
        InputStream is = null;
        BufferedReader br = null;
        StringBuilder result = new StringBuilder();
        try {
            // 创建远程url连接对象
            // 通过远程url连接对象打开一个连接，强转成HTTPURLConnection类
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            // 设置连接超时时间和读取超时时间
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Accept", "application/json");
            if (header != null) {
                header.forEach(conn::setRequestProperty);
            }
            // 发送请求
            conn.connect();
            // 通过conn取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                is = conn.getInputStream();
                br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
            assert conn != null;
            conn.disconnect();
        }
        return result.toString();
    }
    public static String get(String url, int connectTimeout, int readTimeout) {
        HttpURLConnection conn = null;
        InputStream is = null;
        BufferedReader br = null;
        StringBuilder result = new StringBuilder();
        try {
            // 创建远程url连接对象
            // 通过远程url连接对象打开一个连接，强转成HTTPURLConnection类
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            // 设置连接超时时间和读取超时时间
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Accept", "application/json");
            // 发送请求
            conn.connect();
            // 通过conn取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                is = conn.getInputStream();
                br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (is != null) {
                    is.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
            assert conn != null;
            conn.disconnect();
        }
        return result.toString();
    }

    /**
     * 发送文件
     *
     * @param originalFilename 上传的文件名
     * @param inputStream 上传的文件流
     * @return 响应结果
     */
    public static String uploadFile(String originalFilename, InputStream inputStream, String url) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        String result = "";
        try {
            HttpPost httpPost = new HttpPost(url);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setCharset(StandardCharsets.UTF_8);
            // 加上此行代码解决返回中文乱码问题
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            // 文件流
            builder.addBinaryBody("file", inputStream, ContentType.MULTIPART_FORM_DATA,
                    originalFilename);
            HttpEntity entity = builder.build();
            httpPost.setEntity(entity);
            // 执行提交
            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            if (responseEntity != null) {
                // 将响应内容转换为字符串
                result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                httpClient.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }


    public static String get(String url, String data) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            //发送POST请求必须设置为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            //设置连接超时时间和读取超时时间
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            //获取输出流
            out = new OutputStreamWriter(conn.getOutputStream());
            out.write(data);
            out.flush();
            out.close();
            //取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
            } else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }

    public static String get(String url, String data, Map<String, String> header) {
        OutputStreamWriter out = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            // 发送POST请求必须设置为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // 设置连接超时时间和读取超时时间
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (header != null) {
                header.forEach(conn::setRequestProperty);
            }
            // 获取输出流
            out = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
            out.close();
            // 取得输入流，并使用Reader读取
            if (200 == conn.getResponseCode()) {
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    result.append(line);
                }
            }
            else {
                log.error("响应错误！错误代码：" + conn.getResponseCode());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return result.toString();
    }


}
