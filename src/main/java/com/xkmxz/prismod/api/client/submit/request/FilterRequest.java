package com.xkmxz.prismod.api.client.submit.request;

import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.submit.listener.FilterStateListener;
import com.xkmxz.prismod.api.client.submit.listener.FilterEventListener;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 客户端业务请求；参数在执行前统一校验，失败通过最终回执返回。 */
public sealed interface FilterRequest {
    /** 注册 PostChain；logicalId 为空时推导，sessionId 为空时独立管理。 */
    record Register(String ownerId, ResourceLocation logicalId, ResourceLocation postEffect,
                    CustomFilterMetadata metadata, UUID sessionId) implements FilterRequest {
        public Register(String ownerId, ResourceLocation postEffect, CustomFilterMetadata metadata) {
            this(ownerId, null, postEffect, metadata, null);
        }
        public Register(String ownerId, ResourceLocation logicalId, ResourceLocation postEffect,
                        CustomFilterMetadata metadata) {
            this(ownerId, logicalId, postEffect, metadata, null);
        }
    }
    /** 按资源 UUID 释放；重复释放成功且影响数量为零。 */
    record Release(UUID resourceId) implements FilterRequest { }
    /** 清理该 owner 的全部 API 注册。 */
    record ClearRegistrations(String ownerId) implements FilterRequest { }
    /** 临时选择逻辑滤镜，不写玩家配置。 */
    record Select(ResourceLocation filter, float strength) implements FilterRequest { }
    /** 清除临时选择。 */
    record ClearSelection() implements FilterRequest { }
    /** 替换旧强制状态，保留 legacy 优先级规则。 */
    record Force(ResourceLocation filter, float strength) implements FilterRequest { }
    /** 清除旧强制状态。 */
    record ClearForced() implements FilterRequest { }
    /** 创建覆盖；高优先级胜出，同级较新者胜出。 */
    record Override(String ownerId, ResourceLocation filter, float strength, int priority,
                    UUID sessionId) implements FilterRequest {
        public Override(String ownerId, ResourceLocation filter, float strength, int priority) {
            this(ownerId, filter, strength, priority, null);
        }
    }
    /** 清理该 owner 的全部 API 覆盖。 */
    record ClearOverrides(String ownerId) implements FilterRequest { }
    /** 订阅状态，不补发初始快照。 */
    record SubscribeState(String ownerId, FilterStateListener listener) implements FilterRequest { }
    /** 订阅事件；取消时提交 Release。 */
    record SubscribeEvents(String ownerId, FilterEventListener listener) implements FilterRequest { }
    /** 创建只管理自身注册和覆盖的 Session。 */
    record OpenSession(String ownerId) implements FilterRequest { }
    /** 清空 Session 的资源，Session 仍可复用。 */
    record ClearSession(UUID sessionId) implements FilterRequest { }
    /** 关闭 Session 并释放其资源。 */
    record CloseSession(UUID sessionId) implements FilterRequest { }
    /**
     * 在 Session 中逐项注册或创建覆盖，保持顺序，不回滚成功项。
     * owner 必须与 Session 一致；未指定 sessionId 的项目继承批量 Session。
     */
    record Batch(UUID sessionId, List<FilterRequest> requests) implements FilterRequest {
        public Batch {
            requests = requests == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(requests));
        }
    }
}
