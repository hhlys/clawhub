package com.clawhub.edgechat.repo;

import com.clawhub.edgechat.domain.EdgeChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeChatSessionRepository extends JpaRepository<EdgeChatSession, Long> {

    List<EdgeChatSession> findTop30ByOwnerUserIdOrderByLastMessageAtDescUpdatedAtDesc(Long ownerUserId);

    Optional<EdgeChatSession> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    Optional<EdgeChatSession> findByConversationId(String conversationId);
}
