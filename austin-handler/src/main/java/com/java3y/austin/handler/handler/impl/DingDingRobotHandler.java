package com.java3y.austin.handler.handler.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Throwables;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.TaskInfo;
import com.java3y.austin.common.dto.DingDingContentModel;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.handler.domain.dingding.DingDingRobotAccount;
import com.java3y.austin.handler.domain.dingding.DingDingRobotParam;
import com.java3y.austin.handler.domain.dingding.DingDingRobotResult;
import com.java3y.austin.handler.handler.BaseHandler;
import com.java3y.austin.handler.handler.Handler;
import com.java3y.austin.support.utils.AccountUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * 钉钉消息自定义机器人 消息处理器
 *
 * @author 3y
 */
@Slf4j
@Service
public class DingDingRobotHandler extends BaseHandler implements Handler {

    private static final String DING_DING_ROBOT_ACCOUNT_KEY = "dingDingRobotAccount";
    private static final String PREFIX = "ding_ding_robot_";

    @Autowired
    private AccountUtils accountUtils;

    public DingDingRobotHandler() {
        channelCode = ChannelType.DING_DING_ROBOT.getCode();
    }

    @Override
    public boolean handler(TaskInfo taskInfo) {
        try {
            DingDingRobotAccount account = accountUtils.getAccount(taskInfo.getSendAccount(), DING_DING_ROBOT_ACCOUNT_KEY, PREFIX, new DingDingRobotAccount());
            DingDingRobotParam dingDingRobotParam = assembleParam(taskInfo);
            String httpResult = HttpUtil.post(assembleParamUrl(account), JSON.toJSONString(dingDingRobotParam));
            DingDingRobotResult dingDingRobotResult = JSON.parseObject(httpResult, DingDingRobotResult.class);
            if (dingDingRobotResult.getErrCode() == 0) {
                return true;
            }
            // 常见的错误 应当 关联至 AnchorState,由austin后台统一透出失败原因
            log.error("DingDingHandler#handler fail!result:{},params:{}", JSON.toJSONString(dingDingRobotResult), JSON.toJSONString(taskInfo));
        } catch (Exception e) {
            log.error("DingDingHandler#handler fail!e:{},params:{}", Throwables.getStackTraceAsString(e), JSON.toJSONString(taskInfo));
        }
        return false;
    }

    private DingDingRobotParam assembleParam(TaskInfo taskInfo) {

        // 接收者相关
        DingDingRobotParam.AtVO atVo = DingDingRobotParam.AtVO.builder().build();
        if (AustinConstant.SEND_ALL.equals(CollUtil.getFirst(taskInfo.getReceiver()))) {
            atVo.setIsAtAll(true);
        } else {
            atVo.setAtUserIds(new ArrayList<>(taskInfo.getReceiver()));
        }

        // 消息类型以及内容相关
        DingDingContentModel contentModel = (DingDingContentModel) taskInfo.getContentModel();
        return DingDingRobotParam.builder().at(atVo).msgtype("text")
                .text(DingDingRobotParam.TextVO.builder().content(contentModel.getContent()).build()).build();
    }

    /**
     * 拼装 url
     *
     * @param account
     * @return
     */
    private String assembleParamUrl(DingDingRobotAccount account) {
        long currentTimeMillis = System.currentTimeMillis();
        String sign = assembleSign(currentTimeMillis, account.getSecret());
        return (account.getWebhook() + "&timestamp=" + currentTimeMillis + "&sign=" + sign);
    }

    /**
     * 使用HmacSHA256算法计算签名
     *
     * @param currentTimeMillis
     * @param secret
     * @return
     */
    private String assembleSign(long currentTimeMillis, String secret) {
        String sign = "";
        try {
            String stringToSign = currentTimeMillis + String.valueOf(StrUtil.C_LF) + secret;
            Mac mac = Mac.getInstance(AustinConstant.HMAC_SHA256_ENCRYPTION_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(AustinConstant.CHARSET_NAME), AustinConstant.HMAC_SHA256_ENCRYPTION_ALGO));
            byte[] signData = mac.doFinal(stringToSign.getBytes(AustinConstant.CHARSET_NAME));
            sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), AustinConstant.CHARSET_NAME);
        } catch (Exception e) {
            log.error("DingDingHandler#assembleSign fail!:{}", Throwables.getStackTraceAsString(e));
        }
        return sign;
    }
}

