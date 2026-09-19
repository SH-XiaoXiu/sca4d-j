/**
 * 定义乐观版本、调用方版本条件、幂等键、分布式锁和限流要求。
 *
 * <p>{@link com.sca4d.core.api.concurrent.DistributedLock} 负责跨应用实例的互斥执行；{@link
 * com.sca4d.core.api.concurrent.VersionCondition} 负责一次条件写入的预期版本和失败含义；{@link
 * com.sca4d.core.api.concurrent.Version} 表达持久化对象的实际乐观版本。
 */
package com.sca4d.core.api.concurrent;
