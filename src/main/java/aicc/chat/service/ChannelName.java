package aicc.chat.service;

public enum ChannelName {
    CHAT("chat.topic"),
    ALERT("alert.topic"),
    NOTICE("notice.topic");

    private final String value;

    ChannelName(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
