package com.xkmxz.prismod.api.client.query.request;

import com.xkmxz.prismod.api.client.query.view.FilterResourceView;
import com.xkmxz.prismod.api.client.query.view.FilterSnapshot;
import com.xkmxz.prismod.api.client.query.view.FilterDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 有类型的只读查询，不暴露可操作句柄。 */
public sealed interface FilterQuery<R> {
    /** 最近发布的最终状态，包含玩家选择和回退原因。 */
    record Snapshot() implements FilterQuery<FilterSnapshot> { }
    /** 全部或当前可用的滤镜描述。 */
    record Filters(boolean availableOnly) implements FilterQuery<List<FilterDescriptor>> { }
    /** 当前资源；已释放、已失效或未知 UUID 返回空。 */
    record Resource(UUID resourceId) implements FilterQuery<Optional<FilterResourceView>> { }
    /** 指定 owner 的当前资源。 */
    record Resources(String ownerId) implements FilterQuery<List<FilterResourceView>> { }
    /** Session 管理的注册与覆盖，不包含 Session 自身。 */
    record SessionResources(UUID sessionId) implements FilterQuery<List<FilterResourceView>> { }
}
