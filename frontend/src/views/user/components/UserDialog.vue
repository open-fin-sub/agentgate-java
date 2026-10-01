<template>
  <el-dialog
    :model-value="modelValue"
    :title="form.id ? '编辑用户' : '新增用户'"
    width="520px"
    @update:model-value="onVisibleChange"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      :disabled="loading"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model.trim="form.username" placeholder="请输入用户名" maxlength="50" />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model.trim="form.nickname" placeholder="请输入昵称" maxlength="50" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model.trim="form.email" placeholder="请输入邮箱" maxlength="100" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model.trim="form.phone" placeholder="请输入手机号" maxlength="20" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onVisibleChange(false)">取 消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { createUser, updateUser, type UserSaveVO, type UserVO } from '@/api/user'

interface UserForm extends UserSaveVO {
  id?: number
}

const props = defineProps<{
  modelValue: boolean
  // 编辑时传入的用户数据
  data?: UserVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<UserForm>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  status: 1
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '长度不能超过50', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

// 弹窗打开时回填数据
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      if (props.data) {
        Object.assign(form, {
          id: props.data.id,
          username: props.data.username,
          nickname: props.data.nickname,
          email: props.data.email,
          phone: props.data.phone,
          status: props.data.status ?? 1
        })
      } else {
        resetForm()
      }
    }
  }
)

function resetForm() {
  form.id = undefined
  form.username = ''
  form.nickname = ''
  form.email = ''
  form.phone = ''
  form.status = 1
}

function onVisibleChange(val: boolean) {
  emit('update:modelValue', val)
}

function handleClosed() {
  formRef.value?.resetFields()
  resetForm()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const payload: UserSaveVO = {
        username: form.username,
        nickname: form.nickname,
        email: form.email,
        phone: form.phone,
        status: form.status
      }
      if (form.id) {
        await updateUser(form.id, payload)
        ElMessage.success('修改成功')
      } else {
        await createUser(payload)
        ElMessage.success('新增成功')
      }
      emit('success')
      onVisibleChange(false)
    } finally {
      loading.value = false
    }
  })
}
</script>
