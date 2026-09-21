package com.sca4d.core.api.codec;

import com.fasterxml.jackson.databind.JsonNode;

/** 把领域值与已解析 JSON 树互相转换。 */
public interface JsonCodec<V> extends Codec<V, JsonNode> {
}
