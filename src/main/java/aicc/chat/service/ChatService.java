package aicc.chat.service;

import java.util.Map;

public class ChatService {

    String getKV(Map<String, Object> sessionAttributes, String key) {
        if ( sessionAttributes.containsKey(key) ) {
            return String.valueOf(sessionAttributes.get(key) );
        }
        return null;
    }
}
