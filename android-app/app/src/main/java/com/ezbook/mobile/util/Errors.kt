package com.ezbook.mobile.util

import com.ezbook.mobile.data.remote.ApiException
import com.ezbook.mobile.data.remote.SessionExpiredException

/**
 * 把服务端 errorCode / 异常翻译成可读提示。
 * 错误码规则：分类*100000 + 子分类*1000 + 序号（见服务端 pkg/errs/error.go）
 */
fun describeError(error: Throwable): String {
    if (error is SessionExpiredException) {
        return "登录状态已失效，请重新登录"
    }
    if (error is ApiException) {
        return when (error.code) {
            201001, 201002 -> "用户名或密码错误"
            201003 -> "账号已被禁用"
            201020 -> "邮箱尚未验证，请先在网页版完成邮箱验证"
            203000 -> "动态验证码不正确"
            200018 -> "登录失败次数过多，请稍后再试"
            200019 -> "重复提交，请勿重复操作"
            200008 -> "设备时区信息异常，请重新打开应用"
            200000 -> error.message.ifBlank { "提交内容不完整或格式有误" }
            202001, 202002, 202003, 202004, 202005, 202006, 202012 -> "登录状态已失效，请重新登录"
            -102 -> "无法连接服务器，请检查网络或服务器地址"
            -101 -> "服务器地址格式不正确"
            -100 -> "尚未配置服务器地址"
            else -> error.message.ifBlank { "操作失败（错误码 ${error.code}）" }
        }
    }
    return error.message ?: "操作失败，请稍后再试"
}
