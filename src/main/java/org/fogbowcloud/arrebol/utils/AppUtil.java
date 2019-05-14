package org.fogbowcloud.arrebol.utils;

import org.json.JSONObject;

import java.util.Collection;

public class AppUtil {

    public static void makeBodyField(JSONObject json, String key, Boolean bool){
        if (bool != null) {
            json.put(key, bool);
        }
    }

    public static void makeBodyField(JSONObject json, String key, Collection collection){
        if (collection != null && !collection.isEmpty()) {
            json.put(key, collection);
        }
    }

    public static void makeBodyField(JSONObject json, String key, String value) {
        if (value != null && !value.isEmpty()) {
            json.put(key, value);
        }
    }

    public static void makeBodyField(JSONObject json, String key, JSONObject object) {
        if (object != null) {
            json.put(key, object);
        }
    }

    public static String getValueFromJsonStr(String key, String jsonStr) {
        JSONObject json = new JSONObject(jsonStr);
        String value = json.getString(key);
        return value;
    }
}
