/**
 * 拖拽逻辑
 */

import type { ComponentMeta, ComponentNode } from "~/types/builder";

// 放置位置类型
// before: 放在目标之前
// after: 放在目标之后
// inside: 放入目标内部
// inside-left: 放入目标内部的左侧区域
// inside-right: 放入目标内部的右侧区域
export type DropPosition = "before" | "after" | "inside" | "inside-left" | "inside-right" | null;

// 拖拽状态
export interface DragState {
  isDragging: boolean;
  dragType: "new" | "move" | null;
  dragData: ComponentMeta | ComponentNode | null;
  dropTargetId: string | null;
  dropPosition: DropPosition;
}

const dragState = ref<DragState>({
  isDragging: false,
  dragType: null,
  dragData: null,
  dropTargetId: null,
  dropPosition: null,
});

export function useDragDrop() {
  const { addComponent, moveComponent } = useCurrentPage();

  // 开始拖拽新组件
  function startDragNewComponent(meta: ComponentMeta) {
    dragState.value = {
      isDragging: true,
      dragType: "new",
      dragData: meta,
      dropTargetId: null,
      dropPosition: null,
    };
  }

  // 开始拖拽现有组件
  function startDragExistingComponent(component: ComponentNode) {
    dragState.value = {
      isDragging: true,
      dragType: "move",
      dragData: component,
      dropTargetId: null,
      dropPosition: null,
    };
  }

  // 更新放置目标
  function updateDropTarget(
    targetId: string | null,
    position: DropPosition
  ) {
    dragState.value.dropTargetId = targetId;
    dragState.value.dropPosition = position;
  }

  // 结束拖拽
  function endDrag() {
    const { dragType, dragData, dropTargetId, dropPosition } = dragState.value;

    if (dragData && dropPosition) {
      // 如果是容器组件的插槽位置（inside-left 或 inside-right），容器组件已经在内部处理了添加逻辑
      // 这里只需要简单返回，不需要重复添加
      if (dropPosition === "inside-left" || dropPosition === "inside-right") {
        // 容器组件已处理，清理状态即可
        dragState.value = {
          isDragging: false,
          dragType: null,
          dragData: null,
          dropTargetId: null,
          dropPosition: null,
        };
        return;
      }

      if (dragType === "new" && "type" in dragData) {
        // 新增组件
        const meta = dragData as ComponentMeta;
        const parentId = dropPosition === "inside" ? dropTargetId : null;
        const index = dropPosition === "inside" ? undefined : 0; // 简化处理

        addComponent(
          meta.type,
          { ...meta.defaultProps },
          { ...meta.defaultStyle },
          parentId || undefined,
          index
        );
      } else if (dragType === "move" && "id" in dragData) {
        // 移动组件
        const component = dragData as ComponentNode;
        const targetParentId = dropPosition === "inside" ? dropTargetId : null;
        const targetIndex = 0; // 简化处理

        if (component.id !== dropTargetId) {
          moveComponent(component.id, targetParentId, targetIndex);
        }
      }
    }

    // 重置状态
    dragState.value = {
      isDragging: false,
      dragType: null,
      dragData: null,
      dropTargetId: null,
      dropPosition: null,
    };
  }

  // 取消拖拽
  function cancelDrag() {
    dragState.value = {
      isDragging: false,
      dragType: null,
      dragData: null,
      dropTargetId: null,
      dropPosition: null,
    };
  }

  return {
    // 状态
    dragState: readonly(dragState),
    isDragging: computed(() => dragState.value.isDragging),

    // 操作
    startDragNewComponent,
    startDragExistingComponent,
    updateDropTarget,
    endDrag,
    cancelDrag,
  };
}
