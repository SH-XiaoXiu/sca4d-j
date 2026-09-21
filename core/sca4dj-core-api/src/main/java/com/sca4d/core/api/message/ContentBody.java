package com.sca4d.core.api.message;

/** 限制 MessageContent 可以保存的正文来源。 */
public sealed interface ContentBody permits TextContent, BinaryContent, ReferencedContent {
}
