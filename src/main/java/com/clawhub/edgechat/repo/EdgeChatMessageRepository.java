package com.clawhub.edgechat.repo;

import com.clawhub.edgechat.domain.EdgeChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeChatMessageRepository extends JpaRepository<EdgeChatMessage, Long> {

    List<EdgeChatMessage> findTop200BySessionIdOrderBySequenceNoAscIdAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
