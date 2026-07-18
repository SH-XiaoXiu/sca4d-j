package com.sca4d.common.audit;

/**
 * 允许长期保存并在管理端展示的场景审计载荷标记。
 *
 * <p>实现只包含追溯业务动作所需的紧凑数据，不保存凭据、完整请求对象、Entity 或可重新查询的大型快照。
 */
public interface AuditPayload {
}
