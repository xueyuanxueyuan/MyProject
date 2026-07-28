package cn.capinfo.gjj.yhtmock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "yht-mock")
public class YhtMockProperties {

    private final Settlement settlement = new Settlement();
    private final Callback callback = new Callback();

    public Settlement getSettlement() {
        return settlement;
    }

    public Callback getCallback() {
        return callback;
    }

    public static class Settlement {
        /**
         * 结算服务接收一户通回调的地址。
         */
        private String receiveUrl;

        public String getReceiveUrl() {
            return receiveUrl;
        }

        public void setReceiveUrl(String receiveUrl) {
            this.receiveUrl = receiveUrl;
        }
    }

    public static class Callback {
        /**
         * 启动时是否用配置文件覆盖已持久化的页面配置。
         */
        private boolean overrideStoredSettings;
        private boolean autoPushEnabled = true;
        private long delayMs = 800L;
        private boolean pushCaps107 = true;
        private boolean pushCaps205 = true;
        private boolean pushCaps306 = true;
        private boolean pushCaps308 = true;

        public boolean isOverrideStoredSettings() {
            return overrideStoredSettings;
        }

        public void setOverrideStoredSettings(boolean overrideStoredSettings) {
            this.overrideStoredSettings = overrideStoredSettings;
        }

        public boolean isAutoPushEnabled() {
            return autoPushEnabled;
        }

        public void setAutoPushEnabled(boolean autoPushEnabled) {
            this.autoPushEnabled = autoPushEnabled;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public boolean isPushCaps107() {
            return pushCaps107;
        }

        public void setPushCaps107(boolean pushCaps107) {
            this.pushCaps107 = pushCaps107;
        }

        public boolean isPushCaps205() {
            return pushCaps205;
        }

        public void setPushCaps205(boolean pushCaps205) {
            this.pushCaps205 = pushCaps205;
        }

        public boolean isPushCaps306() {
            return pushCaps306;
        }

        public void setPushCaps306(boolean pushCaps306) {
            this.pushCaps306 = pushCaps306;
        }

        public boolean isPushCaps308() {
            return pushCaps308;
        }

        public void setPushCaps308(boolean pushCaps308) {
            this.pushCaps308 = pushCaps308;
        }
    }
}
