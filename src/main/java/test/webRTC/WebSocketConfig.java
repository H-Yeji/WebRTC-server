package test.webRTC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

//    private final SignalingHandler signalingHandler;
//
//    @Autowired
//    public WebSocketConfig(SignalingHandler signalingHandler) {
//        this.signalingHandler = signalingHandler;
//    }
    private final KurentoHandler kurentoHandler;

    @Autowired
    public WebSocketConfig(KurentoHandler kurentoHandler) {
        this.kurentoHandler = kurentoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kurentoHandler, "/ws").setAllowedOrigins("*");
    }
}
