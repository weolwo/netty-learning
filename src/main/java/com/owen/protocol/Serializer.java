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

            // 提前初始化，线程安全
            private final Gson gson = new GsonBuilder()
                    .registerTypeHierarchyAdapter(Class.class, new JsonCodec())
                    .create();

            @Override
            public <T> T deserialize(Class<T> clazz, byte[] bytes) {
                String json = new String(bytes, StandardCharsets.UTF_8);
                return gson.fromJson(json, clazz);
            }

            @Override
            public <T> byte[] serialize(T object) {
                String json = gson.toJson(object);
                return json.getBytes(StandardCharsets.UTF_8);
            }
        },
        HESSIAN {
            // 使用 ThreadLocal 复用输出流，减少 GC 压力
            private final ThreadLocal<ByteArrayOutputStream> BOS_THREAD_LOCAL =
                    ThreadLocal.withInitial(ByteArrayOutputStream::new);

            private final ThreadLocal<ReusableByteArrayInputStream> BIS_THREAD_LOCAL =
                    ThreadLocal.withInitial(ReusableByteArrayInputStream::new);

            // 2. 复用 Hessian2Input 实例
            private final ThreadLocal<Hessian2Input> HESSIAN_IN_THREAD_LOCAL =
                    ThreadLocal.withInitial(() -> new Hessian2Input(null));

            private final ThreadLocal<Hessian2Output> HESSIAN_out_THREAD_LOCAL =
                    ThreadLocal.withInitial(() -> new Hessian2Output(null));

            @Override
            public <T> T deserialize(Class<T> clazz, byte[] bytes) {

                try {
                    ReusableByteArrayInputStream is = BIS_THREAD_LOCAL.get();
                    is.setBuf(bytes); // 重置索引，复用内存
                    Hessian2Input hi = HESSIAN_IN_THREAD_LOCAL.get();
                    hi.init(is);
                    return (T) hi.readObject(clazz);
                } catch (IOException e) {
                    throw new RuntimeException("Hessian反序列化失败，逻辑闭环断裂", e);
                }
            }

            @Override
            public <T> byte[] serialize(T object) {
                ByteArrayOutputStream bos = BOS_THREAD_LOCAL.get();
                bos.reset(); // 重置索引，复用内存
                try {
                    Hessian2Output ho = HESSIAN_out_THREAD_LOCAL.get();
                    ho.init(bos);
                    ho.writeObject(object);
                    ho.flushBuffer();
                    return bos.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException("Hessian 序列化失败", e);
                }
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

    /**
     * 一个简单的包装类，允许直接替换内部的 byte[]
     */
    class ReusableByteArrayInputStream extends ByteArrayInputStream {
        public ReusableByteArrayInputStream() {
            super(new byte[0]);
        }

        public void setBuf(byte[] buf) {
            this.buf = buf;
            this.pos = 0;
            this.count = buf.length;
        }

        public void clear() {
            this.buf = null; // 释放引用，方便 GC
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