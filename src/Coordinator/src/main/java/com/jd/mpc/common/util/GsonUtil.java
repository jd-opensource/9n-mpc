package com.jd.mpc.common.util;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


/**
 * Gson解析工具类
 *
 * 
 * @date 2021/9/26 2:42 下午
 */

public class GsonUtil {

    public static String createGsonString(Object object) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        return gson.toJson(object);
    }

    public static String createGsonStringStream(Object object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    public static <T> T changeGsonToBean(String gsonString, Class<T> cls) {
        Gson gson = new Gson();
        return gson.fromJson(gsonString, cls);
    }

    public static <T> T changeGsonToBean(String gsonString, Type typeOfT) {
        Gson gson = new Gson();
        return gson.fromJson(gsonString, typeOfT);
    }

    public static <T> List<T> changeGsonToList(String gsonString, Class<T> cls) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(gsonString, new TypeReference<List<T>>() {
            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static <T> List<Map<String, T>> changeGsonToListMaps(String gsonString) {
        Gson gson = new Gson();
        return gson.fromJson(gsonString, new TypeToken<List<Map<String, T>>>() {
        }.getType());
    }

    /**
     * 将Map转化为Json
     *
     * @param map
     * @return String
     */
    public static <T> String mapToJson(Map<String, T> map) {
        Gson gson = new Gson();
        String jsonStr = gson.toJson(map);
        return jsonStr;
    }

    public static <T> Map<String, T> changeGsonToMaps(String gsonString) {
        Gson gson = new Gson();
        return gson.fromJson(gsonString, new TypeToken<Map<String, T>>() {
        }.getType());
    }

    public static String readInputStream(InputStream inputStream) {
        BufferedReader reader = null;
        StringBuilder sb = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String tmp;
            while ((tmp = reader.readLine()) != null) {
                sb.append(tmp).append("\n");
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();

    }


    public static String createPrettyString(Object object) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        return gson.toJson(object);
    }

    public static String jackSonString(Object object){
        try {
            return SpringUtil.getBean(ObjectMapper.class).writeValueAsString(object);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

}