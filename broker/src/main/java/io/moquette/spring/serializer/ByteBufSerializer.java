package io.moquette.spring.serializer;

import cn.hutool.core.codec.Base64;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/3/2 16:13
 */
public class ByteBufSerializer extends JsonSerializer<ByteBuf> {
    @Override
    public void serialize(ByteBuf value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        String encode = Base64.encode(ByteBufUtil.getBytes(value));
        gen.writeString(encode);

    }
}
