package com.owen.protocol;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 用于扩展序列化、反序列化算法
 */
public interface Serializer {

    // 反序列化方法
    <T> T deserialize(Class<T> clazz, byte[] bytes);

    // 序列化方法
    <T> byte[] serialize(T object);

    enum Algorithm implements Serializer {

        JAVA {
            @Override
            public <T> T deserialize(Class<T> clazz, byte[] bytes) {
                try {
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
                    return (T) ois.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException("反序列化失败", e);
                }
            }

            @Override
            public <T> byte[] serialize(T object) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(object);
                    return bos.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException("序列化失败", e);
                }
            }
        },

        JSON {
            @Override
            public <T> T deserialize(Class<T> clazz, byte[] bytes) {
                Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(Class.class, new JsonCodec()).create();
                String json = new String(bytes, StandardCharsets.UTF_8);
                return gson.fromJson(json, clazz);
            }

            @Override
            public <T> byte[] serialize(T object) {
                Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(Class.class, new JsonCodec()).create();
                String json = gson.toJson(object);
                return json.getBytes(StandardCharsets.UTF_8);
            }
        },
        HESSIAN {
            @Override
            public <T> T deserialize(Class<T> clazz, byte[] bytes) {
                return HessianCodec.deserialize(bytes);
            }

            @Override
            public <T> byte[] serialize(T object) {
                return HessianCodec.serialize(object);
            }
        }

    }

    /**
     *
     */

    class JsonCodec extends TypeAdapter<Class<?>> {
        @Override
        public void write(JsonWriter out, Class<?> value) throws IOException {
            // 序列化：把 Class 变成字符串 "java.lang.String"
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.getName());
            }
        }

        @Override
        public Class<?> read(JsonReader in) throws IOException {
            // 反序列化：根据字符串 Class.forName 找回类对象
            try {
                return Class.forName(in.nextString());
            } catch (ClassNotFoundException e) {
                throw new IOException("自研协议找不到指定的类，系统血统不匹配", e);
            }
        }
    }

    class HessianCodec {

        public static byte[] serialize(Object obj) {

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                Hessian2Output ho = new Hessian2Output(os);
                ho.writeObject(obj);
                ho.flushBuffer();
                ho.close();
                return os.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Hessian序列化失败，链路受阻", e);
            }
        }

        public static <T> T deserialize(byte[] data) {

            try (ByteArrayInputStream is = new ByteArrayInputStream(data)) {
                Hessian2Input hi = new Hessian2Input(is);
                hi.close();
                return (T) hi.readObject();
            } catch (IOException e) {
                throw new RuntimeException("Hessian反序列化失败，逻辑闭环断裂", e);
            }
        }
    }

    /**
     * Gson 不知道怎么吧java class 对象进行转换，所以需要自己协议额转换器注册到gson的构造器里面，后面用的时候需要用这个到构造器的gson
     */
/*    class JsonCodec implements JsonSerializer<Class<?>>, JsonDeserializer<Class<?>> {

        @Override
        public Class<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                String str = json.getAsString();
                return Class.forName(str);
            } catch (ClassNotFoundException e) {
                throw new JsonParseException(e);
            }
        }

        @Override             //   String.class
        public JsonElement serialize(Class<?> src, Type typeOfSrc, JsonSerializationContext context) {
            // class -> json
            return new JsonPrimitive(src.getName());
        }
    }*/

}