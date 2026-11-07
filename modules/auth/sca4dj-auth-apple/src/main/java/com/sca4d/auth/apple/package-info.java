/**
 * Sign in with Apple 对联邦认证模块的扩展。
 *
 * <table>
 * <caption>本包的概念</caption>
 * <tr><th>类型</th><th>负责</th><th>不负责</th></tr>
 * <tr><td>{@code AppleProviderDriver}</td><td>把 Apple ID Token claims 转换为联邦资料，并提供 {@code client_secret}</td><td>授权码交换、ID Token 签名校验</td></tr>
 * <tr><td>{@code AppleClientSecretGenerator}</td><td>用 Provider 配置里的签名材料签发 ES256 JWT</td><td>保存和加密签名材料</td></tr>
 * </table>
 */
package com.sca4d.auth.apple;
