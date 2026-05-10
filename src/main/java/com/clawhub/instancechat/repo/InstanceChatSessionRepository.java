package com.clawhub.instancechat.repo;

import com.clawhub.instancechat.domain.InstanceChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstanceChatSessionRepository extends JpaRepository<InstanceChatSession, Long> {

    List<InstanceChatSession> findTop30ByOwnerUserIdAndInstanceIdOrderByLastMessageAtDescUpdatedAtDesc(
            Long ownerUserId,
            Long instanceId
    );

    Optional<InstanceChatSession> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
