# Order Department Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix order management department selection so parent and child departments follow the same manual cascade behavior used by role management -> edit -> manage departments.

**Architecture:** Keep the change local to the order query component. Reuse the RoleEdit tree-select approach: enable `check-strictly`, hold a tree ref, and manually add/remove descendant ids when a department node is checked or unchecked.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, Element Plus `el-tree-select`, existing `/@/api/department` API, existing `npm run vue-tsc` verification.

---

## File Structure

- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`
  - Owns the order list query form and current "归属部门" tree-select.
  - Add the same manual descendant selection logic as `RoleEdit.vue`.
  - Preserve existing `props.isContact` behavior that filters private-domain departments.
- Reference only: `v7-shop-admin/src/views/system/vabAutoComponents/RoleEdit.vue`
  - Source of the expected tree-select behavior at lines 39-54 and helper logic at lines 103-129.

No backend change is needed because `OrderManager.vue` already serializes `queryForm.belongDepartmentIds` into the request query at lines 688 and 1135-1137.

---

### Task 1: Mirror RoleEdit Department Tree Behavior In Order Query

**Files:**
- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue:163`
- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue:407`
- Modify: `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue:433`
- Reference: `v7-shop-admin/src/views/system/vabAutoComponents/RoleEdit.vue:39`
- Reference: `v7-shop-admin/src/views/system/vabAutoComponents/RoleEdit.vue:103`

- [ ] **Step 1: Confirm current behavior**

Open order management, expand the "归属部门" selector, and click a parent department with child departments.

Expected before fix: Element Plus default cascade behavior changes parent and child selection automatically, and the resulting `queryForm.belongDepartmentIds` does not match the role edit "管理部门" behavior.

- [ ] **Step 2: Add a ref and strict tree-select props**

In `v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue`, change the "归属部门" tree-select from:

```vue
<el-tree-select
  v-model="queryForm.belongDepartmentIds"
  clearable
  collapse-tags
  :data="allDepartmentTree"
  :default-checked-keys="queryForm.belongDepartmentIds"
  :default-expanded-keys="queryForm.belongDepartmentIds"
  multiple
  node-key="id"
  :props="defaultProps"
  show-checkbox
/>
```

to:

```vue
<el-tree-select
  ref="deptTreeRef"
  v-model="queryForm.belongDepartmentIds"
  clearable
  collapse-tags
  :data="allDepartmentTree"
  :default-checked-keys="queryForm.belongDepartmentIds"
  :default-expanded-keys="queryForm.belongDepartmentIds"
  multiple
  check-strictly
  node-key="id"
  :props="defaultProps"
  show-checkbox
  @check="onDeptTreeCheck"
/>
```

This makes Element Plus stop its built-in parent-child cascade and lets the component control the exact selected id list.

- [ ] **Step 3: Add the tree ref**

Near the existing department state:

```ts
const allDepartmentTree = ref<Department[]>([])
```

add:

```ts
const deptTreeRef = ref<any>(null)
```

- [ ] **Step 4: Add descendant collection helper**

Below `defaultProps`, add the same descendant traversal used by `RoleEdit.vue`:

```ts
const collectDescendantIds = (node: any): number[] => {
  const ids: number[] = []
  if (node.children) {
    for (const child of node.children) {
      ids.push(child.id)
      ids.push(...collectDescendantIds(child))
    }
  }
  return ids
}
```

- [ ] **Step 5: Add manual check handler**

Below `collectDescendantIds`, add:

```ts
const onDeptTreeCheck = (nodeData: any, { checkedKeys }: any) => {
  const tree = deptTreeRef.value
  if (!tree) return

  const isChecked = checkedKeys.includes(nodeData.id)
  let newKeys = [...checkedKeys]

  const descendantIds = collectDescendantIds(nodeData)
  if (isChecked) {
    newKeys = [...new Set([...newKeys, ...descendantIds])]
  } else {
    newKeys = newKeys.filter((key: number) => !descendantIds.includes(key))
  }

  tree.setCheckedKeys(newKeys)
  queryForm.value.belongDepartmentIds = [...newKeys]
}
```

Behavior after this change:
- Checking a parent selects that parent and all descendants.
- Unchecking a parent removes all descendants.
- Checking or unchecking a child does not force its parent state.
- The submitted `belongDepartmentIds` array contains the exact checked keys.

- [ ] **Step 6: Preserve URL/query restore behavior**

Do not change `OrderManager.vue` query restore logic:

```ts
queryForm.belongDepartmentIds =
  typeof query.belongDepartmentIds === 'string' ? query.belongDepartmentIds.split(',') : undefined
```

Do not change request serialization:

```ts
belongDepartmentIds:
  queryForm.belongDepartmentIds && queryForm.belongDepartmentIds.length > 0
    ? queryForm.belongDepartmentIds.join(',')
    : undefined,
```

This keeps existing route query compatibility intact.

- [ ] **Step 7: Run type check**

Run:

```bash
cd v7-shop-admin
npm run vue-tsc
```

Expected: command completes without TypeScript errors.

- [ ] **Step 8: Manual QA in order pages**

Run:

```bash
cd v7-shop-admin
npm run dev
```

Manual checks:
- In `/order/orderManager`, checking a parent department selects its descendants and searching sends all selected department ids.
- In `/order/orderManager`, unchecking a parent department removes its descendants from the selected values.
- In `/order/orderManager`, checking a child department does not automatically check the parent.
- Repeat the same checks in order audit/contact routes if they reuse `OrderQueryParamLayout.vue`.
- In contact mode, `fetchAllDepartments()` still passes `isPrivateDomain = true` and only private-domain departments appear.

- [ ] **Step 9: Commit**

```bash
git add v7-shop-admin/src/views/order/vabAutoComponents/OrderQueryParamLayout.vue
git commit -m "fix: align order department selection cascade"
```

---

## Self-Review

Spec coverage:
- "订单管理，部门选择，上下级不能级联" is covered by `check-strictly`.
- "应该参考角色管理->编辑->管理部门中的部门选择逻辑" is covered by copying the `collectDescendantIds` and `onDeptTreeCheck` pattern from `RoleEdit.vue`.
- Existing order query serialization is preserved.

Placeholder scan:
- No placeholder markers or vague implementation instructions remain.

Type consistency:
- `deptTreeRef`, `collectDescendantIds`, and `onDeptTreeCheck` match the names used in the template.
- `queryForm` remains the existing `defineModel<any>()`, so `queryForm.value.belongDepartmentIds` is correct in script setup.
