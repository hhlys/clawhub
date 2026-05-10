package com.clawhub.instancechat.repo;

import com.clawhub.instancechat.domain.InstanceChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstanceChatMessageRepository extends JpaRepository<InstanceChatMessage, Long> {

    List<InstanceChatMessage> findTop200BySessionIdOrderBySequenceNoAscIdAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
