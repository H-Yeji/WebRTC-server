package test.webRTC;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// SignalingHandler : 웹소켓 연결 처리, TextWebSocketHandler를 상속받아 텍스트 메시지 처리
@Component
public class SignalingHandler extends TextWebSocketHandler {

    // 현재 활성화된 웹소켓 세션 저장
    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());
    // 모든 활성 웹소켓 세션을 저장 (set을 활용해서 세션이 한 번만 저장되도록 보장하고 안정성 증가)
    // Collections.synchronizedSet => 안정성 보장

    // 웹소켓 연결이 설정될 때마다 호출
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessions.contains(session)) {
            System.out.println("Session already exists: " + session.getId());
        } else {
            sessions.add(session);
            System.out.println("Session added: " + session.getId());
        }
        // sessions에 새 session을 추가해서 모든 활성 웹소켓 연결을 추적
        // 추적 => 서버는 나중에 연결된 모든 클라이언트에 메세지를 브로드캐스트하거나, 연결 끊김을 적절히 처리
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println("Received message from client: " + message.getPayload());
        // 이 메소드는 웹소켓 연결을 통해 클라이언트로부터 메시지를 수신할 때마다 호출
        for (WebSocketSession s : sessions) {
            System.out.println("Session ID: " + s.getId() + ", Open: " + s.isOpen());
            System.out.println("Comparing session IDs: " + s.getId() + " with " + session.getId());
            if (s.isOpen()) { // 현재 열려있나 -> 열려있는 세션만 메시지 받기
                // !s.getId().equals(session.getId() : 메시지가 보낸 사람에게 다시 전송되지 않음
                // 서버는 메시지를 보낸 클라이언트를 제외한 다른 모든 클라이언트에게 메시지 전달
                s.sendMessage(message); // 메시지 전송
//                System.out.println("Sent message to client: " + s.getId());
            } else {
                System.out.println("Not sending to session: " + s.getId() + ", Condition failed");
            }
        }
    }
    // 현재 문제 => s.getId와 session.getId가 같음 -> send를 못하고 있음 => 프론트에서 찍혀야하는 로그들이 안찍힘
    // => 최종적으로 수신자 쪽에서 비디오 출력이 안됨 (= 시청이 안됨)
    // && !s.getId().equals(session.getId()) 이거 제외시켜서 임의로 일단 해결 완료
    // 근데 두번째 이슈 => 비디오 출력 (시청)이 안됨

    // 웹소켓 연결이 닫혔을 때 호출
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("Session removed: " + session.getId());
    }
    // 웹소켓 연결이 종료될 때 호출됨 > sessions에서 세션을 제거해, 서버가 더이상 이 클라이언트에 메시지를 보내려고 시도하지 않도록 함
}
