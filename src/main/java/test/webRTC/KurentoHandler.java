package test.webRTC;

import lombok.extern.slf4j.Slf4j;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class KurentoHandler extends TextWebSocketHandler {

    private KurentoClient kurentoClient;
    private MediaPipeline pipeline;
    private WebRtcEndpoint webRtcEndpoint;

    // 방송자
    private WebSocketSession broadcasterSession;
    // 시청자 map
    private final ConcurrentMap<String, WebSocketSession> viewerSessions = new ConcurrentHashMap<>();
    // 최근 offer
    private String currentSdpOffer;

    @Value("${kurento.ws-url}")
    private String kurentoWsUrl;

    // Kurento 미디어 서버와 연결
    @PostConstruct
    public void init() {
        this.kurentoClient = KurentoClient.create(kurentoWsUrl);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JsonObject jsonMessage = JsonParser.parseString(payload).getAsJsonObject();

        String id = jsonMessage.get("id").getAsString();

        if ("broadcaster".equals(id)) { // 방송자와 연결
            broadcasterSession = session;
            log.info("Broadcaster 연결: {}", session.getId());

        } else if ("viewer".equals(id)) { // 시청자와 연결
            viewerSessions.put(session.getId(), session);
            log.info("Viewer connected: {}", session.getId());

            // 방송자가 이미 연결되어 있다면 시청자에게 sdpOffer를 전송
            if (currentSdpOffer != null) {
                JsonObject offerMessage = new JsonObject();
                offerMessage.addProperty("id", "sdpOffer");
                offerMessage.addProperty("sdpOffer", currentSdpOffer);
                session.sendMessage(new TextMessage(offerMessage.toString()));

                log.info("시청자에게 offer 전송: {}", session.getId());
            } else {
                log.warn("No SDP Offer available for viewer: {}", session.getId());
            }
        } else if ("start".equals(id)) {
            // 방송자가 SDP offer를 전송한 경우
            String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
            currentSdpOffer = sdpOffer;
            log.info("방송자로부터 offer 받음");

//            pipeline = kurentoClient.createMediaPipeline();  // 미디어 파이프라인 생성
//            webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();  // WebRtcEndpoint 생성

            try {
                pipeline = kurentoClient.createMediaPipeline();
                log.info("pipeline: {}", pipeline);
            } catch (Exception e) {
                log.error("Error creating MediaPipeline", e);
            }

            try {
                webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
                log.info("webRtcEndpoint: {}", webRtcEndpoint);
            } catch (Exception e) {
                log.error("Error creating WebRtcEndpoint", e);
            }

            String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);  // Kurento에서 SDP Answer 생성
            log.info("sdp answer 생성: {}", sdpAnswer);

            // answer를 방송자에게 전송
            JsonObject response = new JsonObject();
            response.addProperty("id", "sdpAnswer");
            response.addProperty("sdpAnswer", sdpAnswer);
            session.sendMessage(new TextMessage(response.toString()));
            log.info("방송자에게 sdp answer 전송 - kurento가 ");

            // ICE 후보 수집 시작 -> 서버측에서 ice candidate 수집
            log.info("수집전");
            webRtcEndpoint.gatherCandidates();
            log.info("수집후 및 등록 시작 ");
            // ICE 후보가 발견되면 이벤트 처리
            webRtcEndpoint.addIceCandidateFoundListener(event -> {
                JsonObject candidate = new JsonObject();
                candidate.addProperty("id", "iceCandidate");
                candidate.add("candidate", JsonParser.parseString(event.getCandidate().toString()));

                try {
                    session.sendMessage(new TextMessage(candidate.toString()));
                    log.info("ice candidate를 방송자에게 전송: {}", candidate);
                } catch (Exception e) {
                    log.error("Error sending ICE candidate", e);
                }
            });
        } else if ("onIceCandidate".equals(id)) { // 클라이언트쪽에서 ice candidate 생성해서 kurento에 전송
            log.info("ice candidate 수신");

            JsonObject candidateJson = jsonMessage.getAsJsonObject("candidate");
            org.kurento.client.IceCandidate candidate = new org.kurento.client.IceCandidate(
                    candidateJson.get("candidate").getAsString(),
                    candidateJson.get("sdpMid").getAsString(),
                    candidateJson.get("sdpMLineIndex").getAsInt()
            );
            webRtcEndpoint.addIceCandidate(candidate);
            log.info("ICE candidate added: {}", candidate);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        if (session.equals(broadcasterSession)) {
            broadcasterSession = null;
            if (pipeline != null) {
                pipeline.release();
            }
            log.info("방송자 연결 해제: {}", session.getId());
        } else if (viewerSessions.containsKey(session.getId())) {
            viewerSessions.remove(session.getId());
            log.info("시청자 연결 해제: {}", session.getId());
        }
    }
}
