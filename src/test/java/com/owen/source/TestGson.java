package com.owen.source;

import com.owen.protocol.Serializer;
import com.google.gson.*;

public class TestGson {
    public static void main(String[] args) {
        Gson gson = new GsonBuilder().registerTypeAdapter(Class.class, new Serializer.JsonCodec()).create();
        System.out.println(gson.toJson(String.class));
    }
}
