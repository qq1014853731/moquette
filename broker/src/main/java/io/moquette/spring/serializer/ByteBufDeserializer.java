package io.moquette.spring.serializer;

import cn.hutool.core.codec.Base64;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/3/2 16:14
 */
public class ByteBufDeserializer extends JsonDeserializer<ByteBuf> {
    @Override
    public ByteBuf deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String base64Str = p.getValueAsString();
        return Unpooled.copiedBuffer(Base64.decode(base64Str));
    }
}
