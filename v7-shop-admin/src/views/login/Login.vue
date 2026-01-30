<template>
  <div class="login-container">
    <div
      v-show="theme.showLanguage || theme.showColorPicker || theme.showDark"
      class="login-right-tools"
    >
      <vab-language v-show="theme.showLanguage" />
      <vab-color-picker v-show="theme.showColorPicker" />
      <vab-dark v-show="theme.showDark" />
    </div>
    <div class="login-form">
      <img alt="" class="left-img" :src="leftImg" />
      <el-form ref="formRef" label-position="left" :model="form" :rules="rules" @submit.prevent>
        <div class="title">hello !</div>
        <div class="title-tips">{{ translate('欢迎来到') }}{{ nickTitle }}！</div>
        <el-form-item prop="username">
          <el-input
            v-model.trim="form.username"
            v-focus
            clearable
            :placeholder="translate('请输入用户名')"
            type="text"
          >
            <template #prefix>
              <vab-icon icon="user-line" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            ref="passwordRef"
            v-model.trim="form.password"
            clearable
            :placeholder="translate('请输入密码')"
            show-password
            :type="passwordType"
          >
            <template #prefix>
              <vab-icon icon="lock-line" />
            </template>
          </el-input>
        </el-form-item>
        <!-- 验证码验证逻辑需自行开发，如不需要验证码功能建议注释 -->
        <!-- <el-form-item v-if="false" hidden prop="verificationCode">
          <el-input v-model.trim="form.verificationCode" :placeholder="translate('验证码') + previewText" type="text">
            <template #prefix>
              <vab-icon icon="barcode-box-line" />
            </template>
          </el-input>
          <img class="code" :src="codeUrl" @click="changeCode" />
        </el-form-item> -->
        <el-button
          v-throttle="handleLogin"
          class="login-btn"
          :disabled="!loadedCompanyInfo"
          :loading="loading"
          type="primary"
        >
          {{ translate('登录') }}
        </el-button>
        <!-- <router-link v-if="false" hidden to="/register">
          <el-button style="margin-top: 20px; margin-left: -10px" type="primary">
            {{ translate('注册') }}
          </el-button>
        </router-link> -->
      </el-form>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { identity } from '/@/api/company'
import leftImg from '/@/assets/login_images/left_img_1.png'
import { translate } from '/@/i18n'
import { useSettingsStore } from '/@/store/modules/settings'
import { useUserStore } from '/@/store/modules/user'
import { isPassword } from '/@/utils/validate'

defineOptions({
  name: 'Login',
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const settingsStore = useSettingsStore()
const { theme, title, nickTitle, logo } = storeToRefs(settingsStore)
const login = (form: any) => userStore.login(form)
const loading = ref<boolean>(false)
const passwordType = ref<string>('password')
const redirect = ref<any>(undefined)
const timer: any = null
const formRef = ref<any>(null)
const passwordRef = ref<any>(null)
const loadedCompanyInfo = ref<boolean>(false)
const form = reactive<any>({
  username: '',
  password: '',
})

const validateUsername = (rule: any, value: any, callback: any) => {
  if ('' === value) callback(new Error(translate('用户名不能为空')))
  else callback()
}
const validatePassword = (rule: any, value: any, callback: any) => {
  if (isPassword(value)) {
    callback()
  } else {
    callback(new Error(translate('密码不能少于6位')))
  }
}

const rules = reactive<any>({
  username: [
    {
      required: true,
      trigger: 'blur',
      validator: validateUsername,
    },
  ],
  password: [
    {
      required: true,
      trigger: 'blur',
      validator: validatePassword,
    },
  ],
})

const handleRoute = () => {
  return redirect.value === '/404' || redirect.value === '/403' ? '/' : redirect.value
}

const handleLogin = async () => {
  if (formRef.value)
    formRef.value.validate(async (valid: any) => {
      if (valid)
        try {
          loading.value = true
          await login(form).catch(() => {
            loading.value = false
          })
          await router.push(handleRoute())
        } finally {
          loading.value = false
        }
    })
}
const loadCompanyInfo = () => {
  identity()
    .then((companyIdentity) => {
      title.value = companyIdentity.data.name
      logo.value = companyIdentity.data.logo
      nickTitle.value = companyIdentity.data.nick
      loadedCompanyInfo.value = true
      document.title = title.value
    })
    .catch(() => {
      setTimeout(() => {
        loadCompanyInfo()
      }, 1000)
    })
}
onBeforeMount(() => {
  loadCompanyInfo()
  const currentUrl = globalThis.location.href // 当前完整 URL
  console.log(currentUrl, !currentUrl.startsWith('https://admin.'))
  if (
    !currentUrl.startsWith('https://admin.') &&
    !currentUrl.startsWith('http://localhost') &&
    !currentUrl.startsWith('http://192.168.110.117')
  ) {
    // 情况 1：直接跳到 /login
    ElMessageBox.alert('请到总后台登录', '提示', {
      confirmButtonText: '确定',
      callback: () => {
        window.close() // 关闭当前标签页
      },
    })
  }
})

watchEffect(() => {
  redirect.value = (route.query && route.query.redirect) || '/'
})

onBeforeRouteLeave((to, from, next) => {
  try {
    if (timer) clearTimeout(timer)
  } catch {
    /* empty */
  }

  next()
})
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  height: 100vh;
  background: linear-gradient(to top, var(--el-color-primary), var(--el-color-primary-light-3));

  .login-right-tools {
    position: fixed;
    top: var(--el-margin);
    right: var(--el-margin);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: calc(var(--el-padding) / 2);
    background: var(--el-color-white);
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-border-radius-base);

    :deep() {
      .vab-language,
      .vab-color-picker,
      .vab-dark {
        margin: 0 calc(var(--el-padding) / 2) 0 calc(var(--el-padding) / 2) !important;
      }
    }
  }

  @media (max-width: 696px) {
    .login-right-tools {
      display: none;
    }

    .login-form {
      width: 90vw !important;
      margin: auto !important;

      .left-img {
        display: none !important;
      }

      :deep() {
        .el-form--default {
          width: 100% !important;
          margin-right: auto !important;
          margin-left: auto !important;
        }
      }
    }
  }

  @media (min-width: 696px) and (max-width: 999px) {
    .login-right-tools {
      display: none;
    }

    .login-form {
      width: 90vw !important;
      margin: auto !important;

      :deep() {
        .el-form--default {
          width: 50% !important;
        }
      }
    }
  }

  .login-form {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 1000px;
    height: 550px;
    padding: 4.5vh;
    margin: auto;
    overflow: hidden;
    background: var(--el-color-white);
    background-size: 100% 100%;
    border: 1px solid var(--el-border-color);
    border-radius: 15px;
    transition: var(--el-transition);

    .left-img {
      width: 50%;
    }

    :deep() {
      * {
        transition: var(--el-transition);
      }

      .el-form--default {
        width: 45%;
      }

      .title {
        font-size: 54px;
        font-weight: 500;
        color: var(--el-color-black);
      }

      .title-tips {
        margin-top: 29px;
        font-size: 26px;
        font-weight: 400;
        color: var(--el-color-black);
      }

      .login-btn {
        width: 100%;
        height: 50px;
      }

      .el-form-item {
        margin: 20px 0;

        &__error {
          position: absolute;
          font-size: var(--el-font-size-small);
          line-height: 18px;
          color: var(--el-color-error);
        }

        .el-input {
          width: 100%;
          height: 48px;
          line-height: 48px;
        }
      }

      .code {
        position: absolute;
        top: 4px;
        right: 4px;
        cursor: pointer;
        border-radius: var(--el-border-radius-base);
      }
    }
  }
}
</style>
