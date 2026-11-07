/**
 * Google 登录对联邦认证模块的扩展。
 *
 * <table>
 * <caption>本包的概念</caption>
 * <tr><th>类型</th><th>负责</th><th>不负责</th></tr>
 * <tr><td>{@code GoogleProviderDriver}</td><td>把 Google ID Token claims 转换为联邦资料，并检查 {@code hd}</td><td>授权码交换、ID Token 签名校验</td></tr>
 * </table>
 */
package com.sca4d.auth.google;
